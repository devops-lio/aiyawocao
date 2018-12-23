package com.killxdcj.aiyawocao.web.config;

import com.killxdcj.aiyawocao.web.interceptor.RatelimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Autowired
  RatelimitInterceptor ratelimitInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(ratelimitInterceptor)
        .addPathPatterns("/rest/**", "/search/**", "/detail/**", "/recent/**");
  }
}
