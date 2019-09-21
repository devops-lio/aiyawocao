package com.killxdcj.aiyawocao.web.interceptor;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import com.killxdcj.aiyawocao.web.service.MetricsService;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class AntiDefenseInterceptor implements HandlerInterceptor {
  private static final Logger LOGGER = LoggerFactory.getLogger(AntiDefenseInterceptor.class);

  @Value("${ratelimit.ip}")
  private double permitsPerSecond;

  @Value("${antispam.reqlimit.perday}")
  private int reqlimitPerDay;

  @Autowired
  private MetricsService metricsService;

  private LoadingCache<String, RateLimiter> ipRateLimiter;
  private LoadingCache<String /* IP */, AtomicInteger> reqCnt;
  private Set<String> blackAgent;

  private Meter rejectMeterBotReqRateLimit;
  private Meter rejectMeterBlackAgent;
  private Meter rejectMeterReqCntLimit;
  private Meter rejectMeterReqRateLimit;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    String remoteIP = parseRealIP(request);
    if (StringUtils.isEmpty(remoteIP)) {
      LOGGER.warn("get empty ip, {}", request);
      return true;
    }

    String agent = request.getHeader("User-Agent").toLowerCase();
    // black agent
    if (blackAgent.contains(agent)) {
      LOGGER.info("request was reject, ip: {}, black agent: {}, url: {}",
          remoteIP, agent, request.getRequestURI());
      response.sendError(403, "If you need to relax the restrictions, contact the administrator by mail.");
      reqCnt.getUnchecked(remoteIP).addAndGet(reqlimitPerDay + 100);
      rejectMeterBlackAgent.mark();
      return false;
    }

    // limit bot rate
    if (agent.contains("bot") || agent.contains("spider")) {
      if (agent.contains("google")) {
        if (!ipRateLimiter.get("google", () -> RateLimiter.create(0.5)).tryAcquire()) {
          LOGGER.info("Google Bot request was rejected, {}", agent);
          rejectMeterBotReqRateLimit.mark();
          return false;
        }
      } else if (agent.contains("yandex")) {
        if (!ipRateLimiter.get("yandex", () -> RateLimiter.create(0.5)).tryAcquire()) {
          LOGGER.info("Yandex Bot request was rejected, {}", agent);
          rejectMeterBotReqRateLimit.mark();
          return false;
        }
      } else if (agent.contains("baidu") || agent.contains("sougou") || agent.contains("bing") || agent.contains("360")) {
        // white list
      } else {
        if (!ipRateLimiter.get("unknow", () -> RateLimiter.create(0.2)).tryAcquire()) {
          LOGGER.info("Unknow Bot request was rejected, {}", agent);
          rejectMeterBotReqRateLimit.mark();
          return false;
        }
      }
    }

    if (!ipRateLimiter.getUnchecked(remoteIP).tryAcquire()) {
      LOGGER.info("request was rejected, ip:{}, url:{}", remoteIP, request.getRequestURI());
      response.sendError(403, "If you need to relax the restrictions, contact the administrator by mail.");
      rejectMeterReqRateLimit.mark();
      return false;
    }

    // limit req per day
    int reqLastDay = reqCnt.getUnchecked(remoteIP).addAndGet(1);
    if (reqLastDay > reqlimitPerDay) {
      LOGGER.info("request was reject, ip: {}, total req last day: {}, url: {}",
          remoteIP, reqLastDay, request.getRequestURI());
      response.sendError(403, "If you need to relax the restrictions, contact the administrator by mail.");
      rejectMeterReqCntLimit.mark();
      return false;
    }

    return true;
  }

  private String parseRealIP(HttpServletRequest request) {
    String remoteIP = request.getHeader("X-Forwarded-For");
    if (StringUtils.isEmpty(remoteIP)) {
      remoteIP = request.getHeader("X-Real-IP");
    }
    if (StringUtils.isEmpty(remoteIP)) {
      return request.getRemoteHost();
    }

    return remoteIP.split(",")[0];
  }

  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
      ModelAndView modelAndView) throws Exception {

  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception ex) throws Exception {

  }

  @PostConstruct
  public void initInterceptor() {
    ipRateLimiter = CacheBuilder.newBuilder()
        .expireAfterAccess(1, TimeUnit.MINUTES)
        .build(new CacheLoader<String, RateLimiter>() {
          @Override
          public RateLimiter load(String key) throws Exception {
            return RateLimiter.create(0.5);
          }
        });

    reqCnt = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.DAYS)
        .build(new CacheLoader<String, AtomicInteger>() {
          @Override
          public AtomicInteger load(String key) throws Exception {
            return new AtomicInteger(0);
          }
        });

    blackAgent = new HashSet<>();
    blackAgent.add("Mozilla/5.0 (Windows; U; Windows NT 5.1; en-US; rv:1.9.0.3) Gecko/2008092417 Firefox/3.0.3".toLowerCase());

    MetricRegistry registry = metricsService.registry();
    rejectMeterReqCntLimit = registry.meter(MetricRegistry.name(AntiDefenseInterceptor.class, "reject.ReqCntLimit"));
    rejectMeterReqRateLimit = registry.meter(MetricRegistry.name(AntiDefenseInterceptor.class, "reject.ReqRateLimit"));
    rejectMeterBotReqRateLimit = registry.meter(MetricRegistry.name(AntiDefenseInterceptor.class, "reject.BotReqRateLimit"));
    rejectMeterBlackAgent = registry.meter(MetricRegistry.name(AntiDefenseInterceptor.class, "reject.BlackAgent"));
  }
}
