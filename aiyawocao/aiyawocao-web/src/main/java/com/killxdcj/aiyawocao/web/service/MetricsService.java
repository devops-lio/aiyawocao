package com.killxdcj.aiyawocao.web.service;

import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.common.metrics.InfluxdbBackendMetrics;
import com.killxdcj.aiyawocao.common.metrics.InfluxdbBackendMetricsConfig;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetricsService.class);

  @Value("${metrics.influxdb.host}")
  private String host;

  @Value("${metrics.influxdb.port}")
  private int port;

  @Value("${metrics.influxdb.user}")
  private String user;

  @Value("${metrics.influxdb.password}")
  private String password;

  @Value("${metrics.influxdb.name}")
  private String name;

  @Value("${metrics.influxdb.cluster}")
  private String cluster;

  private MetricRegistry metricRegistry;

  @PostConstruct
  public void initMetrics() {
    InfluxdbBackendMetricsConfig config = new InfluxdbBackendMetricsConfig();
    config.setInfluxdbAddr(host + ":" + port);
    config.setInfluxdbUser(user);
    config.setInfluxdbPassword(password);
    config.setInfluxdbName(name);
    config.setCluster(cluster);

    LOGGER.info("init metricservice with {}", config);
    metricRegistry = InfluxdbBackendMetrics.startMetricReport(config);
  }

  @PreDestroy
  public void closeMetrics() {
    InfluxdbBackendMetrics.shutdown();
  }

  public MetricRegistry registry() {
    return metricRegistry;
  }
}
