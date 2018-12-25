package com.killxdcj.aiyawocao.web.controller;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.killxdcj.aiyawocao.web.model.Metadata;
import com.killxdcj.aiyawocao.web.model.SearchResult;
import com.killxdcj.aiyawocao.web.service.ESService;
import com.killxdcj.aiyawocao.web.service.JiebaService;
import com.killxdcj.aiyawocao.web.service.PredictService;
import com.killxdcj.aiyawocao.web.utils.WebUtils;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/")
public class WebController {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebController.class);

  @Value("${ad.enable}")
  private boolean enableAD;

  @Autowired
  private ESService esService;

  @Autowired
  private PredictService predictService;

  @Autowired
  private JiebaService jiebaService;

  private LoadingCache<String, AtomicInteger> hotInfohash = CacheBuilder.newBuilder()
      .expireAfterWrite(24, TimeUnit.HOURS)
      .build(new CacheLoader<String, AtomicInteger>() {
        @Override
        public AtomicInteger load(String key) throws Exception {
          return new AtomicInteger(0);
        }
      });

  @RequestMapping("")
  public String home(Model model) {
    model.addAttribute("hotWords", predictService.getHotSearch());
    return "home";
  }

  @RequestMapping("about")
  public String about() {
    return "about";
  }

  @RequestMapping("search")
  public String search(
      @RequestParam String keyword,
      @RequestParam(value = "p", required = false, defaultValue = "1") int page,
      @RequestParam(value = "s", required = false, defaultValue = "relevance") String sort,
      Model model) {
    return "home";
  }

  @RequestMapping("detail/{magic}/{infohash}")
  public String detail(@PathVariable String magic, @PathVariable String infohash, Model model) {
    return "home";
  }

  @RequestMapping("recent")
  public String recent(@RequestParam(value = "p", required = false, defaultValue = "1") int page,
      Model model) {
    return "home";
  }

  private void addCommon(Model model) {
    if (!enableAD) {
      model.addAttribute("showad", "false");
    }
  }
}
