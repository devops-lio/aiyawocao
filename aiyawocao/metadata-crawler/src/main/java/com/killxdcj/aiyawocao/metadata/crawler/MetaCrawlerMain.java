package com.killxdcj.aiyawocao.metadata.crawler;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.dht.DHT;
import com.killxdcj.aiyawocao.bittorrent.dht.MetaWatcher;
import com.killxdcj.aiyawocao.bittorrent.metadata.MetadataFetcher;
import com.killxdcj.aiyawocao.bittorrent.metadata.MetadataListener;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import com.killxdcj.aiyawocao.common.metrics.InfluxdbBackendMetrics;
import com.killxdcj.aiyawocao.metadata.crawler.config.MetaCrawlerConfig;
import com.killxdcj.aiyawocao.metadata.service.client.MetadataServiceClient;
import java.io.FileNotFoundException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetaCrawlerMain {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetaCrawlerMain.class);

  MetaCrawlerConfig config;
  private MetadataServiceClient client;
  private DHT dht;
  private volatile boolean exit = false;
  private MetricRegistry metricRegistry;
  private MetadataFetcher fetcher = null;

  private Meter metaFetchSuccessed;
  private Meter metaFetchError;
  private Meter metaFetchTimeout;
  private Timer metaFetchSuccessedTimer;
  private Timer metaFetchErrorTimer;
  private Meter metaFetchHasFetched;

  private BlockingQueue<Object[]> pendingAnnouncePeerReq;
  private ExecutorService fetcherSubmitter;

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
    String confPath = "./conf/metadata-crawler.yaml";
    if (args.length > 1) {
      confPath = args[0];
    }
    config = MetaCrawlerConfig.fromYamlConfFile(confPath);

    pendingAnnouncePeerReq = new LinkedBlockingQueue<>(config.getMaxPendingAnnouncePeerReq());
    fetcherSubmitter = Executors.newFixedThreadPool(config.getFetcherSubmitterNum(),
        r -> {
          Thread t = new Thread(r, "FetcherSummiter");
          t.setDaemon(true);
          return t;
        });
    for (int i = 0; i < config.getFetcherSubmitterNum(); i++) {
//      fetcherSubmitter.submit(this::submitNIOMetafetcher);
      fetcherSubmitter.submit(this::submitNIOMetafetcherBatch);
    }

    metricRegistry =
        InfluxdbBackendMetrics.startMetricReport(config.getInfluxdbBackendMetricsConfig());

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
    metricRegistry.register(MetricRegistry.name(MetaCrawlerMain.class, "pendingAnnouncePeerReq"),
        (Gauge<Integer>) () -> pendingAnnouncePeerReq.size());
    metaFetchHasFetched =
        metricRegistry.meter(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchHasFetched"));

    client = new MetadataServiceClient(config.getMetadataServiceClientConfig(), metricRegistry);
    fetcher = new MetadataFetcher(metricRegistry);

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
                try {
                  pendingAnnouncePeerReq.put(new Object[]{infohash, peer});
                } catch (InterruptedException e) {
                  // just ignore
                }
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

    if (fetcherSubmitter != null) {
      fetcherSubmitter.shutdown();
    }

    if (fetcher != null) {
      fetcher.shutdown();
    }

    if (client != null) {
      client.shutdown();
    }

    InfluxdbBackendMetrics.shutdown();

    LOGGER.info("MetaCrawler stoped");
  }

  private void submitNIOMetafetcherBatch() {
    long nextSubmitTime = 0;
    List<Object[]> reqs = new ArrayList<>();
    while (!exit) {
      try {
        Object[] req = pendingAnnouncePeerReq.poll(config.getMaxSubmitInterval(), TimeUnit.MILLISECONDS);
        if (req != null) {
          reqs.add(req);
        }

        if (req != null && reqs.size() < config.getBatchSubmitSize() && System.currentTimeMillis() < nextSubmitTime) {
          continue;
        }

        nextSubmitTime = System.currentTimeMillis() + config.getMaxSubmitInterval();
        List<Boolean> exists = client.doesMetadatasExist(reqs.stream()
            .map(r -> ((BencodedString) r[0]).asHexString().toUpperCase())
            .collect(Collectors.toList()));
        for (int i = 0; i < reqs.size(); i++) {
          if (exists.get(i)) {
            metaFetchHasFetched.mark();
            continue;
          }

          Object[] r = reqs.get(i);
          BencodedString infohash = (BencodedString) r[0];
          Peer peer = (Peer) r[1];
          String infohashStr = infohash.asHexString().toUpperCase();
          doSubmit(infohash, peer, infohashStr);
        }
        reqs.clear();
      } catch (InterruptedException e) {
        return;
      } catch (Exception e) {
        LOGGER.error("submit metafetchers error", e);
      }
    }
  }

  private void submitNIOMetafetcher() {
    while (!exit) {
      try {
        Object[] req = pendingAnnouncePeerReq.take();
        BencodedString infohash = (BencodedString) req[0];
        Peer peer = (Peer) req[1];
        String infohashStr = infohash.asHexString().toUpperCase();
        if (client.doesMetadataExist(infohash.asBytes())) {
          metaFetchHasFetched.mark();
          LOGGER.debug("infohash has been fetched, {}", infohashStr);
          continue;
        }
        doSubmit(infohash, peer, infohashStr);
      } catch (InterruptedException e) {
        return;
      } catch (Exception e) {
        LOGGER.error("submit metafetcher error", e);
      }
    }
  }

  private void doSubmit(BencodedString infohash, Peer peer, String infohashStr) {
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
            if (t instanceof TimeoutException) {
              metaFetchTimeout.mark();
            } else {
              metaFetchError.mark();
            }
            metaFetchErrorTimer.update(costtime, TimeUnit.MILLISECONDS);
            if (LOGGER.isDebugEnabled()) {
              LOGGER.info("meta fetch error, {}, {}, {}, costtime: {}ms", infohashStr, peer,
                  t.getMessage(), costtime);
            }
          }
        });
  }
}
