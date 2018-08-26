package com.killxdcj.aiyawocao.web.controller;

import com.killxdcj.aiyawocao.web.model.Metadata;
import com.killxdcj.aiyawocao.web.model.SearchResult;
import com.killxdcj.aiyawocao.web.service.ESService;
import com.killxdcj.aiyawocao.web.service.PredictService;
import com.killxdcj.aiyawocao.web.utils.WebUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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

  @Autowired private ESService esService;

  @Autowired
  private PredictService predictService;

  @RequestMapping("")
  public String home(Model model) {
    model.addAttribute("hotWords", predictService.getHotWords(10));
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

      SearchResult result = esService.search(keyword, (page - 1) * 10, 10, sort);
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
    } catch (IOException e) {
      LOGGER.error("xx", e);
      return "home";
    }
  }

  @RequestMapping("detail/{magic}/{infohash}")
  public String detail(@PathVariable String magic, @PathVariable String infohash, Model model) {
    try {
      if (!WebUtils.verifyMagic(infohash, magic)) {
        return "home";
      }
      Metadata metadata = esService.detail(infohash);
      model.addAttribute("metadata", metadata);
      List<String> nameKeywords = esService.analyze(metadata.getName());
      List<String> contentKeywords = esService.analyze(String.join(",", metadata.getAllOriginalFiles()));
      Set<String> keywords = new HashSet<>();
      keywords.addAll(nameKeywords.size() > 5 ? nameKeywords.subList(0, 5) : nameKeywords);
      keywords.addAll(contentKeywords.size() > 5 ? contentKeywords.subList(0, 5) : contentKeywords);
      List<String> finalKeywords = new ArrayList<>(keywords);
      Collections.shuffle(finalKeywords);
      model.addAttribute(
          "keywords",
          finalKeywords.size() > 6 ? finalKeywords.subList(0, 6) : finalKeywords);
      return "detail";
    } catch (IOException e) {
      LOGGER.error("query infohash error", e);
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
}
