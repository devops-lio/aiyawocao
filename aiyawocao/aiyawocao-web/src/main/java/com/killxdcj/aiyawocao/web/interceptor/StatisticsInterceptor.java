package com.killxdcj.aiyawocao.web.interceptor;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Timer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.killxdcj.aiyawocao.common.metrics.SquareBracketsMetricNameBuilder;
import com.killxdcj.aiyawocao.web.service.MetricsService;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class StatisticsInterceptor implements HandlerInterceptor {
  private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsInterceptor.class);

  private static final String REQ_HANDLE_START_TIME_KEY = "REQ_HANDLE_START_TIME";

  private static final Map<String, String> BOTS_ROLE_NAME_MAP = new HashMap<String, String>(){{
    put("google", "GoogleBot");
    put("yandex", "YandexBot");
    put("baidu", "BaiduBot");
    put("sogou", "SougouBot");
    put("bing", "BingBot");
    put("360", "360Bot");
  }};

  private static final Map<String, String> METHODS_NAME_MAP = new HashMap<String, String>(){{
    put("/rest", "rest");
    put("/search", "search");
    put("/detail", "detail");
    put("/recent", "recent");
    put("/about", "about");
  }};

  @Autowired
  private MetricsService metricsService;

  private LoadingCache<String, Meter> throughputMetrics;
  private LoadingCache<String, Timer> timerMetrics;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    request.setAttribute(REQ_HANDLE_START_TIME_KEY, System.currentTimeMillis());
    return true;
  }

  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
      ModelAndView modelAndView) throws Exception {
    String role = parseRole(request);
    String server = parseServerName(request);
    String method = parseMethod(request);

    String throughputMetricName = SquareBracketsMetricNameBuilder.newBuilder()
        .setName("aiyawocao.web.anole.throughput")
        .addTagkv("server", server)
        .addTagkv("method", method)
        .addTagkv("role", role)
        .build();
    throughputMetrics.getUnchecked(throughputMetricName).mark();

    String timerMetricName = SquareBracketsMetricNameBuilder.newBuilder()
        .setName("aiyawocao.web.anole.costtime")
        .addTagkv("server", server)
        .addTagkv("method", method)
        .addTagkv("role", role)
        .build();
    long startTime = (long) request.getAttribute(REQ_HANDLE_START_TIME_KEY);
    long costTime = System.currentTimeMillis() - startTime;
    timerMetrics.getUnchecked(timerMetricName).update(costTime, TimeUnit.MILLISECONDS);
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception ex) throws Exception {

  }

  private String parseRole(HttpServletRequest request) {
    String agent = request.getHeader("User-Agent").toLowerCase();

    if (agent.contains("bot") || agent.contains("spider")) {
      for (Map.Entry<String, String> entry : BOTS_ROLE_NAME_MAP.entrySet()) {
        if (agent.contains(entry.getKey())) {
          return entry.getValue();
        }
      }

      return "unknowBot";
    }

    return "manul";
  }

  private String parseServerName(HttpServletRequest request) {
    String serverName = request.getServerName().toLowerCase();
    return serverName.replace(".", "_");
  }

  private String parseMethod(HttpServletRequest request) {
    String uri = request.getRequestURI();
    for (Entry<String, String> entry : METHODS_NAME_MAP.entrySet()) {
      if (uri.startsWith(entry.getKey())) {
        return entry.getValue();
      }
    }
    LOGGER.warn("Unknow method, {}", uri);
    return "unknow";
  }

  @PostConstruct
  public void initInterceptor() {
    throughputMetrics = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build(new CacheLoader<String, Meter>() {
          @Override
          public Meter load(String s) throws Exception {
            return metricsService.registry().meter(s);
          }
        });

    timerMetrics = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.HOURS)
        .build(new CacheLoader<String, Timer>() {
          @Override
          public Timer load(String s) throws Exception {
            return metricsService.registry().timer(s);
          }
        });
  }
}
