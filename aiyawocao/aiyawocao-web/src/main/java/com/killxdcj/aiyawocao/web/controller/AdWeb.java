package com.killxdcj.aiyawocao.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ad/admin")
public class AdWeb {

  @RequestMapping("")
  public String home() {
    return "ad/home";
  }

  @RequestMapping("/search")
  public String search() {
    return "ad/search";
  }

  @RequestMapping("/detail")
  public String detail() {
    return "ad/detail";
  }
}
