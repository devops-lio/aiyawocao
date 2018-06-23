package com.killxdcj.aiyawocao.bittorrent.peer;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class NIOMetaFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(NIOMetaFetcher.class);

	private volatile boolean exit = false;
	private ConcurrentSkipListSet<MetaFetcher> fetchers = new ConcurrentSkipListSet<>();
	private Queue<MetaFetcher> fetcherWaiting = new LinkedBlockingQueue<>();
	private ExecutorService executor = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "NIOMetaFetcher ThreadPool");
		t.setDaemon(true);
		return t;
	});
	private long metafetchTimeout;

	public NIOMetaFetcher(MetricRegistry metricRegistry, long metafetchTimeout, int fetcherConcurrent) {
		this.metafetchTimeout = metafetchTimeout;
		for (int i = 0; i < fetcherConcurrent; i++) {
			executor.submit(this::selectProc);
		}
		executor.submit(this::timeoutFetcherCleanProc);
		metricRegistry.register(MetricRegistry.name(NIOMetaFetcher.class, "runing"),
						(Gauge<Integer>) () -> fetchers.size());
		metricRegistry.register(MetricRegistry.name(NIOMetaFetcher.class, "pending"),
						(Gauge<Integer>) () -> fetcherWaiting.size());
	}

	public void shutdown() {
		executor.shutdown();
	}

	public boolean submit(BencodedString infohash, Peer peer, MetaFetchWatcher watcher) throws Exception {
		MetaFetcher fetcher = new MetaFetcher(infohash, peer, watcher);
		if (fetchers.contains(fetcher)) {
			return false;
		}
		fetcherWaiting.add(fetcher);
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
									executor.submit(() -> fetcher.finish());
									fetchers.remove(fetcher);
									key.cancel();
								}
							} else {
								LOGGER.debug("selectionKey attachment is null");
							}
						}
						selector.selectedKeys().clear();
					}
					startNewFetcher(selector);
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

	private void startNewFetcher(Selector selector) {
		MetaFetcher fetcher = fetcherWaiting.poll();
		while (fetcher != null) {
			if (fetchers.add(fetcher)) {
				try {
					fetcher.start(selector);
					LOGGER.info("meta fetcher started, {}", fetcher);
				} catch (IOException e) {
					fetchers.remove(fetcher);
					LOGGER.error("meta fetcher start error, " + fetcher, e);
				}
			}
			fetcher = fetcherWaiting.poll();
		}
	}

	private void timeoutFetcherCleanProc() {
		Thread.currentThread().setName("NIOMetaFetcher timeout fetcher clean proc");
		while (!exit) {
			try {
				Thread.sleep(60 * 1000);
				List<MetaFetcher> fetchersTimeout = new ArrayList<>();
				for (MetaFetcher fetcher : fetchers) {
					if (fetcher.getElapseTime() > metafetchTimeout) {
						fetchersTimeout.add(fetcher);
					}
				}

				for (MetaFetcher fetcher : fetchersTimeout) {
					fetchers.remove(fetcher);
					fetcher.finish();
				}
				LOGGER.info("NIOMetaFetcher metafetcher cleaned, running:{}, timeout:{}", fetchers.size(), fetchersTimeout.size());
			} catch (InterruptedException e) {
			} catch (Throwable t) {
				LOGGER.error("NIOMetaFetcher timeout clean error", t);
			}
		}
	}
}
