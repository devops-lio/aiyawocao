package com.killxdcj.aiyawocao.common.utils;

import java.util.concurrent.TimeUnit;

public class TimeUtils {
  public static long getCurTime() {
    return System.nanoTime();
  }

  public static long getElapseTime(long preTime) {
    return getElapseTime(preTime, TimeUnit.MILLISECONDS);
  }

  public static long getExpiredTime(long expiredMs) {
    return getCurTime() + TimeUnit.MILLISECONDS.toNanos(expiredMs);
  }

  public static long getElapseTime(long preTime, TimeUnit timeUnit) {
    long duration = System.nanoTime() - preTime;
    switch (timeUnit) {
      case DAYS:
        return TimeUnit.NANOSECONDS.toDays(duration);
      case HOURS:
        return TimeUnit.NANOSECONDS.toHours(duration);
      case MINUTES:
        return TimeUnit.NANOSECONDS.toMinutes(duration);
      case SECONDS:
        return TimeUnit.NANOSECONDS.toSeconds(duration);
      case MILLISECONDS:
        return TimeUnit.NANOSECONDS.toMillis(duration);
      case MICROSECONDS:
        return TimeUnit.NANOSECONDS.toMicros(duration);
      default:
        return duration;
    }
  }
}
