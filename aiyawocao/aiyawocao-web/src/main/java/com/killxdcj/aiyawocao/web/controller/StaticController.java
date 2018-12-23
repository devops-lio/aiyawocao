package com.killxdcj.aiyawocao.web.controller;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.web.service.MetricsService;
import java.util.HashMap;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/static")
public class StaticController {

  private static final Logger LOGGER = LoggerFactory.getLogger(StaticController.class);

  @Autowired
  private MetricsService metricsService;

  private Meter adClickMeter;

  @RequestMapping("/ad/click")
  public Object adClick(@RequestParam(value = "page") String page,
      @RequestParam(value = "bid") String bid,
      @RequestParam(value = "interval") long interval) {
    adClickMeter.mark();
    LOGGER.info("adclick, page:{}, bid:{}, interval:{}", page, bid, interval);
    return new HashMap<String, Object>() {
      {
        put("errmsg", "successed");
      }
    };
  }

  @PostConstruct
  public void initMetrics() {
    MetricRegistry registry = metricsService.registry();
    adClickMeter = registry.meter(MetricRegistry.name(StaticController.class, "ad.click"));
  }

}
