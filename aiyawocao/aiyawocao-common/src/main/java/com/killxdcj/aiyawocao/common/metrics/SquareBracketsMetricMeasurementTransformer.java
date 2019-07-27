package com.killxdcj.aiyawocao.common.metrics;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import metrics_influxdb.api.measurements.MetricMeasurementTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * xx.xx.xx{tag1=tag1v,tag2=tag2v} -> metric name: xx.xx.xx tags: tag1 -> tag1v, tag2 -> tag2v
 */

public class SquareBracketsMetricMeasurementTransformer implements MetricMeasurementTransformer {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(SquareBracketsMetricMeasurementTransformer.class);

  private ConcurrentHashMap<String, Map<String, String>> tagCache = new ConcurrentHashMap<>();
  private ConcurrentHashMap<String, String> nameCache = new ConcurrentHashMap<>();

  @Override
  public Map<String, String> tags(String metricName) {
    return tagCache.computeIfAbsent(metricName, s -> {
      if (s.indexOf("{") == -1) {
        return Collections.EMPTY_MAP;
      }

      if (s.indexOf("{") != -1 && !s.endsWith("}")) {
        LOGGER.warn("Invalid metrics name, will return empty tags, metricName: {}", s);
        return Collections.EMPTY_MAP;
      }

      Map<String, String> tags = new HashMap<>();
      Arrays.stream(s.replace("}", "")
          .split("\\{")[1]
          .split(","))
          .forEach(tagkv -> {
            String[] tmp = tagkv.split("=");
            tags.put(tmp[0], tmp[1]);
          });
      return tags;
    });
  }

  @Override
  public String measurementName(String metricName) {
    return nameCache.computeIfAbsent(metricName, s -> {
      if (s.indexOf("{") == -1) {
        LOGGER.info("Metrics with no tagkvs, metricName: {}", s);
        return s;
      } else {
        return s.split("\\{")[0];
      }
    });
  }

  public static void main(String[] args) {
    SquareBracketsMetricMeasurementTransformer transformer = new SquareBracketsMetricMeasurementTransformer();
    System.out.println(transformer.tags("fdsfs.fsfds.fsfds{a=b,c=d}"));
    System.out.println(transformer.tags("fdsfs.fsfds.fsfds{a=b,c=d}d"));
    System.out.println(transformer.tags("fdsfs.fsfds.fsfds{a=b,c=d}"));

    System.out.println(transformer.measurementName("fdsfs.fsfds.fsfds{a=b,c=d}"));
    System.out.println(transformer.measurementName("fdsfs.fsfds.fsfd545{a=b,c=d}"));
    System.out.println(transformer.measurementName("fdsfs.fsfds.fsfds{h=b,c=d}"));
  }
}
