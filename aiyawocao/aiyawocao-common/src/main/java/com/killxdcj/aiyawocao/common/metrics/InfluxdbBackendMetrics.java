package com.killxdcj.aiyawocao.common.metrics;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import metrics_influxdb.HttpInfluxdbProtocol;
import metrics_influxdb.InfluxdbReporter;

import java.util.concurrent.TimeUnit;

public class InfluxdbBackendMetrics {
  private static ScheduledReporter reporter = null;
  private static MetricRegistry registry = null;

  public static MetricRegistry startMetricReport(InfluxdbBackendMetricsConfig config) {
    if (registry == null) {
      synchronized (InfluxdbBackendMetrics.class) {
        if (registry != null) {
          return registry;
        }

        registry = new MetricRegistry();
        String[] addrs = config.getInfluxdbAddr().split(":");
        reporter =
            InfluxdbReporter.forRegistry(registry)
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
        reporter.start(config.getReportPeriod(), TimeUnit.SECONDS);
        return registry;
      }
    }

    return registry;
  }

  public static void shutdown() {
    if (reporter != null) {
      reporter.stop();
    }
  }
}
