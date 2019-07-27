package com.killxdcj.aiyawocao.web.config;

import com.killxdcj.aiyawocao.web.interceptor.AntiDefenseInterceptor;
import com.killxdcj.aiyawocao.web.interceptor.StatisticsInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Autowired
  AntiDefenseInterceptor antiDefenseInterceptor;

  @Autowired
  StatisticsInterceptor statisticsInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(antiDefenseInterceptor)
        .addPathPatterns("/rest/**", "/search/**", "/detail/**", "/recent/**");
    registry.addInterceptor(statisticsInterceptor)
        .addPathPatterns("/rest/**", "/search/**", "/detail/**", "/recent/**", "/about/**");
  }
}
