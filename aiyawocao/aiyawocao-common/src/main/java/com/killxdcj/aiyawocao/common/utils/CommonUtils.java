package com.killxdcj.aiyawocao.common.utils;

public class CommonUtils {

  private static final double GB = 1.0 * 1024 * 1024 * 1024;
  private static final double MB = 1.0 * 1024 * 1024;
  private static final double KB = 1.0 * 1024;

  public static String fileSize2Human(long sizeBytes) {
    if (sizeBytes > GB) {
      return String.format("%.2f GB", sizeBytes / GB);
    } else if (sizeBytes > MB) {
      return String.format("%.2f MB", sizeBytes / MB);
    } else if (sizeBytes > KB) {
      return String.format("%.2f KB", sizeBytes / KB);
    } else {
      return String.format("%d Byte", sizeBytes);
    }
  }
}
