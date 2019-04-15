package com.killxdcj.aiyawocao.web.controller;

import com.alibaba.fastjson.JSONObject;
import com.killxdcj.aiyawocao.web.service.RedisPoolService;
import java.util.HashMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import redis.clients.jedis.Jedis;

@RestController
@RequestMapping("/ad/rest")
public class AdRest {
  public static final Logger LOGGER = LoggerFactory.getLogger(RestfulController.class);

  public static final String AD_CONFIG = "ad_config";

  @Autowired
  private RedisPoolService redisPoolService;

  @RequestMapping(value = "set", produces = MediaType.APPLICATION_JSON_VALUE)
  public Object update(@RequestBody JSONObject content) {
    try (Jedis jedis = redisPoolService.getJedis()) {
      jedis.hset(AD_CONFIG, content.getString("id"), content.toJSONString());
    }
    return new HashMap<String, Object>() {{
      put("errno", 0);
      put("errmsg", "succeed");
    }};
  }

  @RequestMapping(value = "get", produces = MediaType.APPLICATION_JSON_VALUE)
  public Object get(@RequestParam("id") String id) {
    try (Jedis jedis = redisPoolService.getJedis()) {
      String config = jedis.hget(AD_CONFIG, id);
      if (StringUtils.isEmpty(config)) {
        return new HashMap<String, Object>() {{
          put("enable", false);
        }};
      } else {
        return config;
      }
    }
  }
}
