package com.killxdcj.aiyawocao.meta.centre.wrapper;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.killxdcj.aiyawocao.meta.centre.config.MetaCentreConfig;
import com.killxdcj.aiyawocao.meta.manager.AliOSSBackendMetaManager;
import com.killxdcj.aiyawocao.meta.manager.MetaManager;
import com.killxdcj.aiyawocao.meta.manager.exception.MetaNotExistException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import metrics_influxdb.HttpInfluxdbProtocol;
import metrics_influxdb.InfluxdbReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository("aliOSSBAckendMetaCentreWrapper")
public class AliOSSBAckendMetaCentreWrapper implements InitializingBean, DisposableBean {
  private static final Logger LOGGER = LoggerFactory.getLogger(AliOSSBAckendMetaCentreWrapper.class);

  @Autowired
  private MetaCentreConfig metaCentreConfig;

  private MetaManager metaManager;

  public boolean exist(String infohash) {
    return metaManager.doesMetaExist(infohash);
  }

  public byte[] get(String infohash) throws IOException, MetaNotExistException {
    return metaManager.get(infohash);
  }

  public void put(String infohash, byte[] meta) {
    metaManager.put(infohash, meta);
  }

  public void shutdown() {
    this.metaManager.shutdown();
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    MetricRegistry metricRegistry = new MetricRegistry();
    String[] addrs = metaCentreConfig.getInfluxdbAddr().split(":");
    ScheduledReporter reporter = InfluxdbReporter.forRegistry(metricRegistry)
        .protocol(new HttpInfluxdbProtocol("http", addrs[0], Integer.parseInt(addrs[1]),
            metaCentreConfig.getInfluxdbUser(), metaCentreConfig.getInfluxdbPassword(), metaCentreConfig
            .getInfluxdbName()))
        .convertRatesTo(TimeUnit.SECONDS)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .filter(MetricFilter.ALL)
        .tag("cluster", metaCentreConfig.getCluster())
        .build();
    reporter.start(60, TimeUnit.SECONDS);
    this.metaManager = new AliOSSBackendMetaManager(metricRegistry, metaCentreConfig.getMetaManagerConfig());
  }

  @Override
  public void destroy() throws Exception {
//        this.metaManager.shutdown();
  }
}
