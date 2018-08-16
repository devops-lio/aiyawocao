package com.killxdcj.aiyawocao.web.controller;

import com.killxdcj.aiyawocao.web.model.Metadata;
import com.killxdcj.aiyawocao.web.service.ESService;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class WebController {
  private static final Logger LOGGER = LoggerFactory.getLogger(WebController.class);

  @Autowired
  private ESService esService;

  @RequestMapping("hello/{user}")
  public String hello(@PathVariable String user, Model model) {
    model.addAttribute("user", user);
    return "hello";
  }

  @RequestMapping("search/{key}")
  public String search(@PathVariable String key, Model model) {
    return "";
  }

  @RequestMapping("detail/{infohash}")
  public String detail(@PathVariable String infohash, Model model) {
    try {
      model.addAttribute("metadata", new Metadata(esService.detail(infohash)));
      return "detail";
    } catch (IOException e) {
      LOGGER.error("query infohash error", e);
      return "index";
    }

  }
}
