package com.killxdcj.aiyawocao.web.interceptor;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class RatelimitInterceptor implements HandlerInterceptor {
  private static final Logger LOGGER = LoggerFactory.getLogger(RatelimitInterceptor.class);

  @Value("${ratelimit.ip}")
  private double permitsPerSecond;

  LoadingCache<String, RateLimiter> ipRateLimiter;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    String remoteIP = request.getHeader("X-Forwarded-For");
    if (StringUtils.isEmpty(remoteIP)) {
      remoteIP = request.getHeader("x-real-ip");
    }
    if (StringUtils.isEmpty(remoteIP)) {
      return true;
    }

    remoteIP = remoteIP.split(",")[0];
    if (!ipRateLimiter.getUnchecked(remoteIP).tryAcquire()) {
      LOGGER.info("request was rejected, ip:{}, url:{}", remoteIP, request.getRequestURI());
      response.sendRedirect("/");
      return false;
    }

    return true;
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
            return RateLimiter.create(1);
          }
        });
  }
}
