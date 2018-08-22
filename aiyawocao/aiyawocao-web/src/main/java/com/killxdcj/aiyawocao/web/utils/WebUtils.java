package com.killxdcj.aiyawocao.web.utils;

import org.apache.commons.codec.digest.DigestUtils;

public class WebUtils {
  private static final String MAGIC_KEY = "aiyawocao";

  public static String calcMagic(String infohash) {
    return DigestUtils.md5Hex(infohash + MAGIC_KEY).substring(0, 5).toUpperCase();
  }

  public static boolean verifyMagic(String infohash, String magic) {
    return calcMagic(infohash).equals(magic);
  }
}
