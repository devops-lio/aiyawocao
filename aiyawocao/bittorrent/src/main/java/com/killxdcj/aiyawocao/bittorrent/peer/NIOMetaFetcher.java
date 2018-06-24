package com.killxdcj.aiyawocao.bittorrent.peer;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.config.MetaFetchConfig;
import com.killxdcj.aiyawocao.bittorrent.utils.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

public class NIOMetaFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(NIOMetaFetcher.class);

	private volatile boolean exit = false;
	private ExecutorService executor = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "NIOMetaFetcher ThreadPool");
		t.setDaemon(true);
		return t;
	});
	private long metafetchTimeout;
	private int blackThreshold;

	private LoadingCache<String, NodeFetchersManager> nodeFetchersMgr;
	private Cache<String, Object> blackNodeMgr;
	private Queue<String> pendingNode = new LinkedBlockingQueue<>();
	private ConcurrentHashMap<MetaFetcher, Long> runningFetchers = new ConcurrentHashMap<>();
	private Meter ignoreByNodeMeter;

	public NIOMetaFetcher(MetaFetchConfig config, MetricRegistry metricRegistry) {
		this.metafetchTimeout = config.getMetafetchTimeoutMs();
		this.blackThreshold = config.getBlackThreshold();
		nodeFetchersMgr = CacheBuilder.newBuilder()
				.expireAfterAccess(config.getMetafetchTimeoutMs() * 2, TimeUnit.MILLISECONDS)
				.build(new CacheLoader<String, NodeFetchersManager>() {
					@Override
					public NodeFetchersManager load(String s) throws Exception {
						return new NodeFetchersManager();
					}
				});
		blackNodeMgr = CacheBuilder.newBuilder()
				.expireAfterWrite(config.getBlackTimeMs(), TimeUnit.MILLISECONDS)
				.build();

		for (int i = 0; i < config.getFetcherNum(); i++) {
			executor.submit(this::selectProc);
		}
		executor.submit(this::timeoutFetcherCleanProc);
		metricRegistry.register(MetricRegistry.name(NIOMetaFetcher.class, "runing"),
				(Gauge<Integer>) () -> runningFetchers.size());
		metricRegistry.register(MetricRegistry.name(NIOMetaFetcher.class, "pending"),
				(Gauge<Integer>) () -> pendingNode.size());
		metricRegistry.register(MetricRegistry.name(NIOMetaFetcher.class, "blacknode"),
				(Gauge<Long>) () -> blackNodeMgr.size());
		ignoreByNodeMeter = metricRegistry.meter(MetricRegistry.name(NIOMetaFetcher.class, "ignoreByNode"));
	}

	public void shutdown() {
		executor.shutdown();
	}

	public boolean submit(BencodedString infohash, Peer peer, MetaFetchWatcher watcher) throws Exception {
		String nodekey = buildNodeKey(peer);
		if (blackNodeMgr.getIfPresent(nodekey) != null) {
			ignoreByNodeMeter.mark();
			return false;
		}

		MetaFetcher fetcher = new MetaFetcher(infohash, peer, watcher);
		if (runningFetchers.containsKey(fetcher)) {
			return false;
		}

		boolean first = nodeFetchersMgr.getUnchecked(nodekey).add(fetcher);
		if (first) {
			pendingNode.add(nodekey);
		}
		LOGGER.info("meta fetcher submited, {}", fetcher);
		return true;
	}

	private void selectProc() {
		Thread.currentThread().setName("NIOMetaFetcher Selector Proc");
		Selector selector = null;
		try {
			selector = Selector.open();
		} catch (IOException e) {
			LOGGER.error("Selector open error", e);
			return;
		}

		try {
			while (!exit) {
				try {
					if (selector.select(1000) > 0) {
						for (SelectionKey key : selector.selectedKeys()) {
							MetaFetcher fetcher = (MetaFetcher) key.attachment();
							if (fetcher != null) {
								if (fetcher.execute()) {
									String nodekey = buildNodeKey(fetcher.getPeer());
									executor.submit(() -> fetcher.finish());
									runningFetchers.remove(fetcher);
									key.cancel();
									NodeFetchersManager fetchersManager = nodeFetchersMgr.getUnchecked(nodekey);
									if (fetchersManager.updateResult(fetcher.getResult()) >= blackThreshold ) {
										blackNodeMgr.put(nodekey, new Object());
										fetchersManager.clean();
										LOGGER.info("{} failed too much, add to blk list", nodekey);
									} else {
										startNextNodeFetcher(nodekey, selector);
									}
								}
							} else {
								LOGGER.debug("selectionKey attachment is null");
							}
						}
						selector.selectedKeys().clear();
					}
					startNewNodeFetcher(selector);
				} catch (IOException e) {
					LOGGER.error("NIOMetaFetcher select proc error", e);
					return;
				}
			}
		} finally {
			if (selector != null) {
				try {
					selector.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private void startNewNodeFetcher(Selector selector) {
		String nodekey = pendingNode.poll();
		while (nodekey != null) {
			startNextNodeFetcher(nodekey, selector);
			nodekey = pendingNode.poll();
		}
	}

	private void startNextNodeFetcher(String nodekey, Selector selector) {
		MetaFetcher fetcher = nodeFetchersMgr.getUnchecked(nodekey).get();
		if (fetcher == null) {
			return;
		}

		try {
			fetcher.start(selector);
			runningFetchers.put(fetcher, TimeUtils.getCurTime());
			LOGGER.info("meta fetcher started, {}", fetcher);
		} catch (IOException e) {
			LOGGER.error("meta fetcher start error, " + fetcher, e);
		}
	}

	private void timeoutFetcherCleanProc() {
		Thread.currentThread().setName("NIOMetaFetcher timeout fetcher clean proc");
		while (!exit) {
			try {
				Thread.sleep(60 * 1000);
				List<MetaFetcher> fetchersTimeout = new ArrayList<>();
				for (MetaFetcher fetcher : runningFetchers.keySet()) {
					if (fetcher.getElapseTime() > metafetchTimeout) {
						fetchersTimeout.add(fetcher);
					}
				}

				for (MetaFetcher fetcher : fetchersTimeout) {
					runningFetchers.remove(fetcher);
					fetcher.finish();
				}
				LOGGER.info("NIOMetaFetcher metafetcher cleaned, running:{}, timeout:{}", runningFetchers.size(),
						fetchersTimeout.size());
			} catch (InterruptedException e) {
			} catch (Throwable t) {
				LOGGER.error("NIOMetaFetcher timeout clean error", t);
			}
		}
	}

	private String buildNodeKey(Peer peer) {
		return "" + peer.getAddr().getHostAddress() + ":" + peer.getPort()/100;
	}
}
