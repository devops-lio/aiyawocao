package com.killxdcj.aiyawocao.meta.crawler;

import com.codahale.metrics.*;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.dht.DHT;
import com.killxdcj.aiyawocao.bittorrent.dht.MetaWatcher;
import com.killxdcj.aiyawocao.bittorrent.metadata.MetadataListener;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import com.killxdcj.aiyawocao.meta.crawler.config.MetaCrawlerConfig;
import com.killxdcj.aiyawocao.metadata.service.client.MetadataServiceClient;
import metrics_influxdb.HttpInfluxdbProtocol;
import metrics_influxdb.InfluxdbReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MetaCrawlerMain {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetaCrawlerMain.class);

  MetaCrawlerConfig config;
  private MetadataServiceClient client;
  private DHT dht;
  private volatile boolean exit = false;
  private ScheduledReporter reporter;
  private MetricRegistry metricRegistry;
  private com.killxdcj.aiyawocao.bittorrent.metadata.MetadataFetcher fetcher = null;

  private Meter metaFetchSuccessed;
  private Meter metaFetchError;
  private Meter metaFetchTimeout;
  private Timer metaFetchSuccessedTimer;
  private Timer metaFetchErrorTimer;

  public static void main(String[] args) {
    MetaCrawlerMain metaCrawlerMain = new MetaCrawlerMain();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                new Runnable() {
                  @Override
                  public void run() {
                    metaCrawlerMain.shutdown();
                  }
                }));
    try {
      metaCrawlerMain.start(args);
    } catch (Exception e) {
      LOGGER.error("crawler start failed", e);
    }
  }

  public void start(String[] args) throws FileNotFoundException, SocketException {
    LOGGER.info("args = {}", Arrays.toString(args));
    String confPath = "./conf/crawler.yaml";
    if (args.length > 1) {
      confPath = args[0];
    }
    config = MetaCrawlerConfig.fromYamlConfFile(confPath);

    metricRegistry = new MetricRegistry();
    String[] addrs = config.getInfluxdbAddr().split(":");
    reporter =
        InfluxdbReporter.forRegistry(metricRegistry)
            .protocol(
                new HttpInfluxdbProtocol(
                    "http",
                    addrs[0],
                    Integer.parseInt(addrs[1]),
                    config.getInfluxdbUser(),
                    config.getInfluxdbPassword(),
                    config.getInfluxdbName()))
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .filter(MetricFilter.ALL)
            .tag("cluster", config.getCluster())
            .build();
    reporter.start(60, TimeUnit.SECONDS);

    metaFetchSuccessed =
        metricRegistry.meter(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchSuccessed"));
    metaFetchError =
        metricRegistry.meter(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchError"));
    metaFetchTimeout =
        metricRegistry.meter(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchTimeout"));
    metaFetchSuccessedTimer =
        metricRegistry.timer(
            MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchSuccessedCost"));
    metaFetchErrorTimer =
        metricRegistry.timer(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchErrorCost"));
    client = new MetadataServiceClient(config.getMetadataServiceClientConfig());
    fetcher = new com.killxdcj.aiyawocao.bittorrent.metadata.MetadataFetcher(metricRegistry);

    dht =
        new DHT(
            config.getBittorrentConfig(),
            new MetaWatcher() {
              @Override
              public void onGetInfoHash(BencodedString infohash) {
                LOGGER.debug("catch infohash : {}", infohash.asHexString());
              }

              @Override
              public void onAnnouncePeer(BencodedString infohash, Peer peer) {
                submitNIOMetafetcher(infohash, peer);
              }
            },
            metricRegistry);

    while (!exit) {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        return;
      }
    }
  }

  public void shutdown() {
    LOGGER.info("Shutdown MetaCrawler ...");
    if (dht != null) {
      LOGGER.info("Shutdown dht");
      dht.shutdown();
    }

    if (fetcher != null) {
      fetcher.shutdown();
    }

    if (reporter != null) {
      reporter.stop();
    }

    if (client != null) {
      client.shutdown();
    }

    LOGGER.info("MetaCrawler stoped");
  }

  private void submitNIOMetafetcher(BencodedString infohash, Peer peer) {
    String infohashStr = infohash.asHexString();
    if (client.doesMetadataExist(infohash.asBytes())) {
      LOGGER.info("infohash has been fetched, {}", infohashStr);
      return;
    }

    try {
      fetcher.submit(
          infohash,
          peer,
          new MetadataListener() {
            @Override
            public void onSuccedded(
                Peer peer, BencodedString infohash, byte[] metadata, long costtime) {
              LOGGER.info("meta fetched, {}, {}, costtime: {}ms", infohashStr, peer, costtime);
              metaFetchSuccessed.mark();
              metaFetchSuccessedTimer.update(costtime, TimeUnit.MILLISECONDS);
              if (client.doesMetadataExist(infohash.asBytes())) {
                LOGGER.info("{} has been fetched by others", infohashStr);
                return;
              }
              try {
                client.putMetadata(infohash.asBytes(), metadata);
                LOGGER.info("{} meta uploaded", infohashStr);
              } catch (Throwable t) {
                LOGGER.error("upload metadata error, " + infohashStr, t);
              }
            }

            @Override
            public void onFailed(Peer peer, BencodedString infohash, Throwable t, long costtime) {
              LOGGER.info("meta fetch error, {}, {}, costtime: {}ms", infohashStr, peer, costtime);
              if (t instanceof TimeoutException) {
                metaFetchTimeout.mark();
              } else {
                metaFetchError.mark();
              }
              metaFetchErrorTimer.update(costtime, TimeUnit.MILLISECONDS);
              LOGGER.error(infohashStr + ", " + peer + " meta fetch error", t);
            }
          });
    } catch (Exception e) {
      LOGGER.error("submit metafetcher error", e);
    }
  }
}
