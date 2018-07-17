package com.killxdcj.aiyawocao.bittorrent.metadata;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataFetcher {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetadataFetcher.class);

  private EventLoopGroup eventLoopGroup = new NioEventLoopGroup(20, r -> {
    Thread t = new Thread(r);
    t.setName("MetadataFetcher NIOThread");
    t.setDaemon(true);
    return t;
  });
  private ExecutorService executorService = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r);
    t.setName("MetadataFetcher Executor");
    t.setDaemon(true);
    return t;
  });

  private PeerTaskManager peerTaskManager = new PeerTaskManager();

  public MetadataFetcher() {
  }

  public MetadataFetcher(MetricRegistry metricRegistry) {
    metricRegistry.register(MetricRegistry.name(MetadataFetcher.class, "runing"),
        (Gauge<Integer>) () -> peerTaskManager.getRunning());
    metricRegistry.register(MetricRegistry.name(MetadataFetcher.class, "pending"),
        (Gauge<Integer>) () -> peerTaskManager.getPending());
  }

  public void shutdown() {
    eventLoopGroup.shutdownGracefully();
    executorService.shutdown();
  }

  public void submit(BencodedString infohash, Peer peer, MetadataListener listener) {
    boolean isNewPeer = peerTaskManager.submitTask(new Task(infohash, peer, listener));

    if (isNewPeer) {
      new PeerFetcher(peer, peerTaskManager, eventLoopGroup, executorService);
    }
  }
}
