package com.killxdcj.aiyawocao.web.controller;

import java.util.Enumeration;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/test")
public class TestController {
  private static final Logger LOGGER = LoggerFactory.getLogger(TestController.class);

  @RequestMapping("header")
  public Object testHeader(HttpServletRequest request) {
    Enumeration<String> headers = request.getHeaderNames();
    while (headers.hasMoreElements()) {
      String name = headers.nextElement();
      LOGGER.info("{} -> {}", name, request.getHeader(name));
    }
    return "";
  }

}
