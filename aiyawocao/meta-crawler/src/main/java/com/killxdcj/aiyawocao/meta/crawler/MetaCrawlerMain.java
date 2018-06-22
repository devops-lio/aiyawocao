package com.killxdcj.aiyawocao.meta.crawler;

import com.codahale.metrics.*;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class MetaCrawlerMain {
	private static final Logger LOGGER = LoggerFactory.getLogger(MetaCrawlerMain.class);

	MetaCrawlerConfig config;
	private MetaManager metaManager;
	private DHT dht;
	private volatile boolean exit = false;
	private ConcurrentMap<MetaFetcherKey, Future> fetcherMap = new ConcurrentHashMap<>();
	private ExecutorService executorService = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "MetaFetcher ThreadPool");
		t.setDaemon(true);
		return t;
	});
	private ScheduledReporter reporter;
	private MetricRegistry metricRegistry;
	private LoadingCache<String, AtomicInteger> infohashConcurrentFetchCntMap;
	private LoadingCache<String, AtomicInteger> nodeConcurrentFetchCntMap;
	private int infohashMaxConcurrentFetch;
	private int nodeMaxConcurrentFetch;
	private Thread timeoutFetcherCleaner;

	private Meter metaFetchSuccessed;
	private Meter metaFetchError;
	private Meter metaFetchTimeout;
	private Timer metaFetchSuccessedTimer;
	private Timer metaFetchErrorTimer;
	private Meter metaFetchIgnore;

	public void start(String[] args) throws FileNotFoundException, SocketException {
		LOGGER.info("args = {}", Arrays.toString(args));
		String confPath = "./conf/crawler.yaml";
		if (args.length > 1) {
			confPath = args[0];
		}
		config = MetaCrawlerConfig.fromYamlConfFile(confPath);
		infohashMaxConcurrentFetch = config.getInfohashMaxConcurrentFetch();
		nodeMaxConcurrentFetch = config.getNodeMaxConcurrentFetch();

		infohashConcurrentFetchCntMap = CacheBuilder.newBuilder()
						.expireAfterAccess(config.getMetaFetchTimeout(), TimeUnit.MILLISECONDS)
						.build(new CacheLoader<String, AtomicInteger>() {
							@Override
							public AtomicInteger load(String s) throws Exception {
								return new AtomicInteger(0);
							}
						});
		nodeConcurrentFetchCntMap = CacheBuilder.newBuilder()
						.expireAfterAccess(config.getMetaFetchTimeout(), TimeUnit.MILLISECONDS)
						.build(new CacheLoader<String, AtomicInteger>() {
							@Override
							public AtomicInteger load(String s) throws Exception {
								return new AtomicInteger(0);
							}
						});

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
		metaFetchSuccessedTimer = metricRegistry.timer(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchSuccessedCost"));
		metaFetchErrorTimer = metricRegistry.timer(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchErrorCost"));
		metricRegistry.register(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchRunning"),
						(Gauge<Integer>) () -> fetcherMap.size());
		metaFetchIgnore = metricRegistry.meter(MetricRegistry.name(MetaCrawlerMain.class, "DHTMetaFetchIgnore"));

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

		if (timeoutFetcherCleaner != null) {
			timeoutFetcherCleaner.interrupt();
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

		AtomicInteger infohashFetchCnt = infohashConcurrentFetchCntMap.getUnchecked(infohashStr);
		if (infohashFetchCnt.get() >= infohashMaxConcurrentFetch ) {
			LOGGER.debug("{} ignore by info concurrent fetch limit", infohashStr);
			metaFetchIgnore.mark();
			return;
		}
		AtomicInteger nodeFetchCnt = nodeConcurrentFetchCntMap.getUnchecked(peer.getAddr().getHostAddress());
		if (nodeFetchCnt.get() >= nodeMaxConcurrentFetch) {
			LOGGER.debug("{} ignore by node concurrent fetch limit, node:{}:{}",
					infohashStr, peer.getAddr().getHostAddress(), peer.getPort());
			metaFetchIgnore.mark();
			return;
		}
		infohashFetchCnt.incrementAndGet();
		nodeFetchCnt.incrementAndGet();

		fetcherMap.computeIfAbsent(new MetaFetcherKey(infohashStr, peer, config.getMetaFetchTimeout()), new Function<MetaFetcherKey, Future>() {
			@Override
			public Future apply(MetaFetcherKey metaFetcherKey) {
				LOGGER.info("{} {}:{} meta fetch start", infohashStr, peer.getAddr(), peer.getPort());
				long start = TimeUtils.getCurTime();
				return executorService.submit(new MetadataFetcher(peer, infohash,
								new MetadataFetcher.IFetcherCallback() {
									@Override
									public void onFinshed(BencodedString infohash1, byte[] metadata) {
										LOGGER.info("{} {}:{} meta fetched, costtime:{}", infohashStr, peer.getAddr().getHostAddress(), peer.getPort(), TimeUtils.getElapseTime(start));
										fetcherMap.remove(new MetaFetcherKey(infohashStr, peer, 0)).cancel(true);
										infohashFetchCnt.decrementAndGet();
										nodeFetchCnt.decrementAndGet();

										metaFetchSuccessed.mark();
										metaFetchSuccessedTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
										if (metaManager.doesMetaExist(infohashStr)) {
											LOGGER.info("{} has been fetched by others", infohashStr);
											return;
										}

										metaManager.put(infohashStr, metadata);
										LOGGER.info("{} meta uploaded", infohashStr);
									}

									@Override
									public void onException(Exception e) {
										LOGGER.info("{} {}:{} meta fetch error, costtime:{}", infohashStr, peer.getAddr().getHostAddress(), peer.getPort(), TimeUtils.getElapseTime(start));
										fetcherMap.remove(new MetaFetcherKey(infohashStr, peer, 0)).cancel(true);
										infohashFetchCnt.decrementAndGet();
										nodeFetchCnt.decrementAndGet();
										metaFetchError.mark();
										metaFetchErrorTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
//										if (LOGGER.isDebugEnabled()) {
											LOGGER.error(infohashStr + " " + peer.getAddr().getHostAddress() + ":" + peer.getPort() + " meta fetch error", e);
//										}
									}
								}));
			}
		});
	}

	private void startTimeoutFetcherCleaner() {
		timeoutFetcherCleaner = new Thread(() -> {
			Thread.currentThread().setName("TimeoutFetcher Cleaner");
			while (!exit) {
				try {
					Thread.sleep(60 * 1000);
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
				} catch (InterruptedException e) {
				} catch (Throwable t) {
					LOGGER.error("timeoutFetcherCleaner error", t);
				}
			}
		});
		timeoutFetcherCleaner.start();
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
