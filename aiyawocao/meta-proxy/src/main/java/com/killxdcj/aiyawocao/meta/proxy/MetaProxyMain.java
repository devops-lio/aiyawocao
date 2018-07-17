package com.killxdcj.aiyawocao.meta.proxy;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.killxdcj.aiyawocao.meta.manager.AliOSSBackendMetaManager;
import com.killxdcj.aiyawocao.meta.manager.MetaManager;
import com.killxdcj.aiyawocao.meta.proxy.config.MetaProxyConfig;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import java.io.FileNotFoundException;
import java.util.concurrent.TimeUnit;
import metrics_influxdb.HttpInfluxdbProtocol;
import metrics_influxdb.InfluxdbReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetaProxyMain {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetaProxyMain.class);

  private Channel channel;
  private MetaManager metaManager;
  private ScheduledReporter reporter;

  public static void main(String[] args) {
    try {
      MetaProxyMain metaProxy = new MetaProxyMain();
      Runtime.getRuntime().addShutdownHook(new Thread(() -> metaProxy.shutdown()));
      metaProxy.start(args);
    } catch (Exception e) {
      LOGGER.error("MetaProxy error", e);
    }
  }

  public void start(String[] args) throws FileNotFoundException, InterruptedException {
    String confFile = "./conf/meta-proxy.yaml";
    if (args.length > 1) {
      confFile = args[0];
    }

    MetaProxyConfig config = MetaProxyConfig.fromYamlConf(confFile);

    MetricRegistry metricRegistry = new MetricRegistry();
    String[] addrs = config.getInfluxdbAddr().split(":");
    reporter = InfluxdbReporter.forRegistry(metricRegistry)
        .protocol(new HttpInfluxdbProtocol("http", addrs[0], Integer.parseInt(addrs[1]),
            config.getInfluxdbUser(), config.getInfluxdbPassword(), config.getInfluxdbName()))
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .filter(MetricFilter.ALL)
        .tag("cluster", config.getCluster())
        .build();
    reporter.start(60, TimeUnit.SECONDS);

    metaManager = new AliOSSBackendMetaManager(metricRegistry, config.getMetaManagerConfig());

    EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    EventLoopGroup workGroup = new NioEventLoopGroup();
    try {
      ServerBootstrap b = new ServerBootstrap();
      b.option(ChannelOption.SO_BACKLOG, 1024);
      b.group(bossGroup, workGroup).channel(NioServerSocketChannel.class).childHandler(new ChannelInitializer<SocketChannel>() {
        @Override
        protected void initChannel(SocketChannel ch) throws Exception {
          ChannelPipeline p = ch.pipeline();
          p.addLast(new HttpServerCodec());
          p.addLast(new HttpObjectAggregator(config.getMaxContentLength()));
          p.addLast(new HttpResuestHandler(metaManager));
        }
      });
      channel = b.bind(config.getPort()).sync().channel();
      LOGGER.info("MetaProxy started in {}", config.getPort());
      channel.closeFuture().sync();
    } finally {
      bossGroup.shutdownGracefully();
      workGroup.shutdownGracefully();
    }
  }

  public void shutdown() {
    channel.close();
    metaManager.shutdown();
    reporter.stop();
    LOGGER.info("MetaProxy shutdowned");
  }
}
