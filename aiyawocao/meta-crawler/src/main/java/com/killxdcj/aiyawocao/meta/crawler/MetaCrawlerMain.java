package com.killxdcj.aiyawocao.meta.crawler;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.dht.DHT;
import com.killxdcj.aiyawocao.bittorrent.dht.MetaWatcher;
import com.killxdcj.aiyawocao.bittorrent.peer.MetadataFetcher;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import com.killxdcj.aiyawocao.bittorrent.utils.TimeUtils;
import com.killxdcj.aiyawocao.meta.crawler.config.MetaCrawlerConfig;
import com.killxdcj.aiyawocao.meta.manager.AliOSSBackendMetaManager;
import com.killxdcj.aiyawocao.meta.manager.MetaManager;
import metrics_influxdb.HttpInfluxdbProtocol;
import metrics_influxdb.InfluxdbReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;

public class MetaCrawlerMain {
	private static final Logger LOGGER = LoggerFactory.getLogger(MetaCrawlerMain.class);

	MetaCrawlerConfig config;
	private MetaManager metaManager;
	private DHT dht;
	private volatile boolean exit = false;
	private ConcurrentMap<MetaFetcherKey, Future> fetcherMap = new ConcurrentHashMap<>();
	private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10, r -> {
		Thread t = new Thread(r, "Crawler ThreadPool");
		t.setDaemon(true);
		return t;
	});
	private ScheduledReporter reporter;
	private MetricRegistry metricRegistry;

	private Meter metaFetchSuccessed;
	private Meter metaFetchError;
	private Meter metaFetchTimeout;

	public void start(String[] args) throws FileNotFoundException, SocketException {
		LOGGER.info("args = {}", Arrays.toString(args));
		String confPath = "./conf/crawler.yaml";
		if (args.length > 1) {
			confPath = args[0];
		}
		config = MetaCrawlerConfig.fromYamlConfFile(confPath);

		metricRegistry = new MetricRegistry();
		String[] addrs = config.getInfluxdbAddr().split(":");
		reporter = InfluxdbReporter.forRegistry(metricRegistry)
						.protocol(new HttpInfluxdbProtocol("http", addrs[0], Integer.parseInt(addrs[1]),
								config.getInfluxdbUser(), config.getInfluxdbPassword(), config.getInfluxdbName()))
						.convertRatesTo(TimeUnit.SECONDS)
						.convertDurationsTo(TimeUnit.MILLISECONDS)
						.filter(MetricFilter.ALL)
						.build();
		reporter.start(60, TimeUnit.SECONDS);

		metaFetchSuccessed = metricRegistry.meter(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchSuccessed"));
		metaFetchError = metricRegistry.meter(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchError"));
		metaFetchTimeout = metricRegistry.meter(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchTimeout"));

		metaManager = new AliOSSBackendMetaManager(metricRegistry, config.getMetaManagerConfig());
		dht = new DHT(config.getBittorrentConfig(), new MetaWatcher() {
			@Override
			public void onGetInfoHash(BencodedString infohash) {
				LOGGER.info("catch infohash : {}", infohash.asHexString());
			}

			@Override
			public void onAnnouncePeer(BencodedString infohash, Peer peer) {
				submitMetaFetcher(infohash, peer);
			}
		}, metricRegistry);

		startTimeoutFetcherCleaner();

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

		executorService.shutdown();

		if (metaManager != null) {
			LOGGER.info("Shutdown metamanager");
			metaManager.shutdown();
		}

		if (reporter != null) {
			reporter.stop();
		}

		LOGGER.info("MetaCrawler stoped");
	}

	private void submitMetaFetcher(BencodedString infohash, Peer peer) {
		String infohashStr = infohash.asHexString();
		if (metaManager.doesMetaExist(infohashStr)) {
			LOGGER.info("infohash has been fetched, {}", infohashStr);
			return;
		}

		fetcherMap.computeIfAbsent(new MetaFetcherKey(infohashStr, peer, config.getMetaFetchTimeout()), new Function<MetaFetcherKey, Future>() {
			@Override
			public Future apply(MetaFetcherKey metaFetcherKey) {
				LOGGER.info("{} {}:{} meta fetch start", infohashStr, peer.getAddr(), peer.getPort());
				return executorService.submit(new MetadataFetcher(peer, infohash,
								new MetadataFetcher.IFetcherCallback() {
									@Override
									public void onFinshed(BencodedString infohash1, byte[] metadata) {
										metaFetchSuccessed.mark();
										LOGGER.info("{} {}:{} meta fetched", infohashStr, peer.getAddr(), peer.getPort());
										if (metaManager.doesMetaExist(infohashStr)) {
											LOGGER.info("{} has been fetched by others", infohashStr);
											return;
										}

										metaManager.put(infohashStr, metadata);
										fetcherMap.remove(new MetaFetcherKey(infohashStr, peer, 0)).cancel(true);
										LOGGER.info("{} meta uploaded", infohashStr);
									}

									@Override
									public void onException(Exception e) {
										metaFetchError.mark();
										LOGGER.info("{} {}:{} meta fetch error", infohashStr, peer.getAddr(), peer.getPort());
										fetcherMap.remove(new MetaFetcherKey(infohashStr, peer, 0)).cancel(true);
//										if (LOGGER.isDebugEnabled()) {
											LOGGER.error(infohashStr + " " + peer.getAddr() + ":" + peer.getPort() + " meta fetch error", e);
//										}
									}
								}));
			}
		});
	}

	private void startTimeoutFetcherCleaner() {
		executorService.scheduleAtFixedRate(() -> {
			long cur = TimeUtils.getCurTime();
			List<MetaFetcherKey> timeOutFetcher = new ArrayList<>();
			for (Map.Entry<MetaFetcherKey, Future> entry : fetcherMap.entrySet()) {
				if (cur >= entry.getKey().expireTime) {
					timeOutFetcher.add(entry.getKey());
					entry.getValue().cancel(true);
					LOGGER.warn("{} {} fetch timeout, ", entry.getKey().infohashStr, entry.getKey().peer);
				}
			}
			for (MetaFetcherKey key : timeOutFetcher) {
				fetcherMap.remove(key);
			}
			metaFetchTimeout.mark(timeOutFetcher.size());
			LOGGER.info("timeouted meta fetcher cleaned, timeout:{}, running:{}", timeOutFetcher.size(), fetcherMap.size());
		}, 60, 60, TimeUnit.SECONDS);
	}

	private class MetaFetcherKey {
		private String infohashStr;
		private Peer peer;
		private long expireTime;

		public MetaFetcherKey(String infohashStr, Peer peer, long timeout) {
			this.infohashStr = infohashStr;
			this.peer = peer;
			this.expireTime = TimeUtils.getExpiredTime(timeout);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			MetaFetcherKey that = (MetaFetcherKey) o;

			if (infohashStr != null ? !infohashStr.equals(that.infohashStr) : that.infohashStr != null) return false;
			return peer != null ? peer.equals(that.peer) : that.peer == null;
		}

		@Override
		public int hashCode() {
			int result = infohashStr != null ? infohashStr.hashCode() : 0;
			result = 31 * result + (peer != null ? peer.hashCode() : 0);
			return result;
		}
	}

	public static void main(String[] args) {
		MetaCrawlerMain metaCrawlerMain = new MetaCrawlerMain();
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
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
}
