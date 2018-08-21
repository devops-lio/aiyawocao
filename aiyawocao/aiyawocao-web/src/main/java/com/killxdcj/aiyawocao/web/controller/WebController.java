package com.killxdcj.aiyawocao.web.controller;

import com.killxdcj.aiyawocao.web.model.SearchResult;
import com.killxdcj.aiyawocao.web.service.ESService;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/")
public class WebController {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebController.class);

  @Autowired
  private ESService esService;

  @RequestMapping("")
  public String home() {
    return "home";
  }

  @RequestMapping("search")
  public String search(@RequestParam String keyword,
      @RequestParam(value = "p", required = false, defaultValue = "1") int page,
      Model model) {
    try {
      SearchResult result = esService.search(keyword, (page - 1) * 10, 10);
      model.addAttribute("result", result);
      model.addAttribute("keyword", keyword);

      long totalPage = result.getTotalHits() / 10 + (result.getTotalHits() % 10 > 0 ? 1 : 0);
      model.addAttribute("curPage", page);
      if (totalPage <= 10) {
        model.addAttribute("pageNum", totalPage);
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
      return "search";
    } catch (IOException e) {
      LOGGER.error("xx", e);
      return "home";
    }
  }

  @RequestMapping("detail/{infohash}")
  public String detail(@PathVariable String infohash, Model model) {
    try {
      model.addAttribute("metadata", esService.detail(infohash));
      return "detail";
    } catch (IOException e) {
      LOGGER.error("query infohash error", e);
      return "home";
    }

  }
}
