package com.killxdcj.aiyawocao.meta.centre.controller;

import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.meta.centre.config.MetaCentreConfig;
import com.killxdcj.aiyawocao.meta.centre.wrapper.AliOSSBAckendMetaCentreWrapper;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("meta")
public class MetaController {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetaController.class);
  @Autowired
  MetaCentreConfig metaCentreConfig;
  @Autowired
  private AliOSSBAckendMetaCentreWrapper aliOSSBAckendMetaCentreWrapper;

  @RequestMapping("{infohash}/exist")
  public Object exist(@PathVariable(value = "infohash") String infohash) {
    return new HashMap<String, Object>() {{
      put("errno", 0);
      put("infohash", infohash);
      put("exist", aliOSSBAckendMetaCentreWrapper.exist(infohash));
    }};
  }

  @RequestMapping("{infohash}/parse")
  public Object parse(@PathVariable(value = "infohash") String infohash) {
    try {
      byte[] metadata = aliOSSBAckendMetaCentreWrapper.get(infohash);
      Bencoding bencoding = new Bencoding(metadata);
      return new HashMap<String, Object>() {{
        put("errno", 0);
        put("info", bencoding.decode().toHuman());
      }};
    } catch (Exception e) {
      LOGGER.error("parse infohash error, " + infohash, e);
      return new HashMap<String, Object>() {{
        put("errno", -1);
        put("errmsg", e.getMessage());
      }};
    }
  }

  @RequestMapping("{infohash}/put")
  public Object put(@PathVariable(value = "infohash") String infohash, @RequestBody byte[] meta) {
    try {
      aliOSSBAckendMetaCentreWrapper.put(infohash, meta);
      LOGGER.info("saved {}", infohash);
      return new HashMap<String, Object>() {{
        put("errno", 0);
      }};
    } catch (Exception e) {
      LOGGER.error("save infohash error, " + infohash, e);
      return new HashMap<String, Object>() {{
        put("errno", -1);
        put("errmsg", e.getMessage());
      }};
    }
  }
}
