package com.killxdcj.aiyawocao.web.controller;

import com.killxdcj.aiyawocao.web.model.Metadata;
import com.killxdcj.aiyawocao.web.service.ESService;
import java.io.IOException;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest")
public class RestfulController {

  private static final Logger LOGGER = LoggerFactory.getLogger(RestfulController.class);

  @Autowired
  private ESService esService;

  @RequestMapping(value = "/search/{keyword}", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public Object search(@PathVariable String keyword,
      @RequestParam(value = "from", required = false, defaultValue = "0") int from,
      @RequestParam(value = "size", required = false, defaultValue = "10") int size) {
    try {
      return esService.searchx(keyword, from, size);
    } catch (IOException e) {
      LOGGER.error("search error", e);
      return new HashMap<String, Object>() {{
        put("errmsg", "inter error");
      }};
    }
  }

  @RequestMapping(value = "/detail/{infohash}", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public Object detail(@PathVariable String infohash) {
    try {
      Metadata metadata = esService.detail(infohash);
      if (metadata == null) {
        return new HashMap<String, Object>() {{
          put("errmsg", "not exist");
        }};
      }
      return metadata.getOriginalData();
    } catch (IOException e) {
      LOGGER.error("query infohash error", e);
      return new HashMap<String, Object>() {{
        put("errmsg", "inter error");
      }};
    }
  }
}
