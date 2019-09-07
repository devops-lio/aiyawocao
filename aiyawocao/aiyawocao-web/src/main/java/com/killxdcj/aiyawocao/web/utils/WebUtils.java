package com.killxdcj.aiyawocao.web.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;

public class WebUtils {
  private static final String MAGIC_KEY = "aiyawocao";
  private static final Set<String> IMG_TYPES = new HashSet<String>(Arrays.asList("bmp", "jpg",
      "jpeg", "png", "tiff", "gif", "pcx", "tga", "exif", "fpx", "svg", "psd", "cdr", "pcd", "dxf",
      "ufo", "eps", "ai", "raw", "wmf"));
  private static final Set<String> DOC_TYPES = new HashSet<>(Arrays.asList("txt", "doc", "docx",
      "xls", "htm", "html", "jsp", "rtf", "wpd", "pdf", "ppt"));
  private static final Set<String> VIDEO_TYPES = new HashSet<>(Arrays.asList("mp4", "avi", "mov",
      "wmv", "asf", "navi", "3gp", "mkv", "f4v", "rmvb", "webm"));
  private static final Set<String> MUSIC_TYPES = new HashSet<>(Arrays.asList("mp3", "wma", "wav",
      "mod", "ra", "cd", "md", "asf", "aac", "vqf", "ape", "mid", "ogg", "m4a", "vqf"));
  private static final String DEFAULT_FILE_ICON = "<i class=\"fa fa-file-o\" aria-hidden=\"true\"></i>";

  public static String calcMagic(String infohash) {
    return DigestUtils.md5Hex(infohash + MAGIC_KEY).substring(0, 5).toUpperCase();
  }

  public static boolean verifyMagic(String infohash, String magic) {
    return calcMagic(infohash).equals(magic);
  }

  public static String calcAwesomeIcon(String fileName) {
    if (StringUtils.isEmpty(fileName)) {
      return "<i class=\"fa fa-file-o\" aria-hidden=\"true\"></i>";
    }

    String fileType = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length()).toLowerCase();
    if (IMG_TYPES.contains(fileType)) {
      return "<i class=\"fa fa-file-image-o\" aria-hidden=\"true\"></i>";
    } else if (VIDEO_TYPES.contains(fileType)) {
      return "<i class=\"fa fa-file-video-o\" aria-hidden=\"true\"></i>";
    } else if (DOC_TYPES.contains(fileType)) {
      return DEFAULT_FILE_ICON;
    } else if (MUSIC_TYPES.contains(fileType)) {
      return "<i class=\"fa fa-file-audio-o\" aria-hidden=\"true\"></i>";
    }

    return DEFAULT_FILE_ICON;
  }

  public static String parseRealIPFromRequest(HttpServletRequest request) {
    String remoteIP = request.getHeader("X-Forwarded-For");
    if (StringUtils.isEmpty(remoteIP)) {
      remoteIP = request.getHeader("x-real-ip");
    }
    if (StringUtils.isEmpty(remoteIP)) {
      return request.getRemoteHost();
    }

    return remoteIP.split(",")[0];
  }
}
