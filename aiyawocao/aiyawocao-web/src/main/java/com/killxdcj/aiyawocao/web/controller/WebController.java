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
    try {
      if (page == 1) {
        predictService.markRequest(keyword);
      }

      SearchResult result = esService.search(keyword, (page - 1) * 10, 10, sort, false);
      if (result.getTotalHits() == 0) {
        LOGGER.info("search hits is empty, use fuzzyQuery, {}", keyword);
        result = esService.search(keyword, (page - 1) * 10, 10, sort, true);
      }
      model.addAttribute("result", result);
      model.addAttribute("keyword", keyword);

      long totalPage = result.getTotalHits() / 10 + (result.getTotalHits() % 10 > 0 ? 1 : 0);
      model.addAttribute("curPage", page);
      if (totalPage <= 10) {
        model.addAttribute("pageNum", totalPage == 0 ? 1 : totalPage);
        model.addAttribute("startPage", 1);
      } else {
        model.addAttribute("pageNum", 10);
        model.addAttribute("startPage", page - 4 < 1 ? 1 : page - 4);
        if (page + 5 > totalPage) {
          model.addAttribute("startPage", totalPage - 9);
        }
      }
      model.addAttribute("pre", page - 1 > 0 ? page - 1 : 1);
      model.addAttribute("next", page + 1 > totalPage ? totalPage : page + 1);
      model.addAttribute("sort", sort);
      return "search";
    } catch (Throwable e) {
      LOGGER.error("handle search error", e);
      return "home";
    }
  }

  @RequestMapping("detail/{magic}/{infohash}")
  public String detail(@PathVariable String magic, @PathVariable String infohash, Model model) {
    try {
      infohash = infohash.toUpperCase();
      magic = magic.toUpperCase();
      if (!WebUtils.verifyMagic(infohash, magic)) {
        LOGGER.warn("magic verify failed, infohash:{}, magic:{}", infohash, magic);
        return "home";
      }

      if (infohash.equals("315854C6D68D5155C70D8C6E1B3869A6BDA28B08") || infohash.equals("B9571A124BC1F23068F5BDF0FE263F49380FA5BB")) {
        return "home";
      }

      Metadata metadata = esService.detail(infohash);
      model.addAttribute("metadata", metadata);
      List<String> keywords = jiebaService.analyze(metadata.getName());
      model.addAttribute("keywords", keywords.size() > 6 ? keywords.subList(0, 6) : keywords);
      AtomicInteger hot = hotInfohash.getUnchecked(infohash.toUpperCase());
      model.addAttribute("showad", "true");
      if (hot.incrementAndGet() <= 2) {
        model.addAttribute("showad", "false");
      }
      addCommon(model);
      return "detail";
    } catch (IOException e) {
      LOGGER.error("handle detail error, " + infohash, e);
      return "home";
    }
  }

  @RequestMapping("recent")
  public String recent(@RequestParam(value = "p", required = false, defaultValue = "1") int page,
      Model model) {
    try {
      SearchResult result = esService.recent((page - 1) * 10, 10);
      model.addAttribute("result", result);
      model.addAttribute("keyword", "");

      long totalPage = result.getTotalHits() / 10 + (result.getTotalHits() % 10 > 0 ? 1 : 0);
      model.addAttribute("curPage", page);
      if (totalPage <= 10) {
        model.addAttribute("pageNum", totalPage == 0 ? 1 : totalPage);
        model.addAttribute("startPage", 1);
      } else {
        model.addAttribute("pageNum", 10);
        model.addAttribute("startPage", page - 4 < 1 ? 1 : page - 4);
        if (page + 5 > totalPage) {
          model.addAttribute("startPage", totalPage - 9);
        }
      }
      model.addAttribute("pre", page - 1 > 0 ? page - 1 : 1);
      model.addAttribute("next", page + 1 > totalPage ? totalPage : page + 1);
      model.addAttribute("sort", "date");
      return "recent";
    } catch (IOException e) {
      LOGGER.error("recent error", e);
      return "home";
    }
  }

  private void addCommon(Model model) {
    if (!enableAD) {
      model.addAttribute("showad", "false");
    }
  }
}
