package com.killxdcj.aiyawocao.web.controller;

import com.killxdcj.aiyawocao.web.service.ESService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/web")
public class WebController {

  @Autowired
  private ESService esService;

  @RequestMapping("/hello/{user}")
  public String hello(@PathVariable String user, Model model) {
    model.addAttribute("user", user);
    return "hello";
  }

  @RequestMapping("/search/{key}")
  public String search(@PathVariable String key, Model model) {
    return "";
  }

  @RequestMapping("/detail/{infohash}")
  public String detail(@PathVariable String infohash, Model model) {
    return "";
  }
}
