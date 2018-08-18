package com.killxdcj.aiyawocao.metadata.service.client;

public class MetadataServiceClientConfig {
  private int poolsize = 10;
  private String server = "btproxy.host:10242";
  private boolean enableLocalCache = true;
  private int expiredTime = 20 * 60 * 1000;

  public int getPoolsize() {
    return poolsize;
  }

  public void setPoolsize(int poolsize) {
    this.poolsize = poolsize;
  }

  public String getServer() {
    return server;
  }

  public void setServer(String server) {
    this.server = server;
  }

  public boolean getEnableLocalCache() {
    return enableLocalCache;
  }

  public void setEnableLocalCache(boolean enableLocalCache) {
    this.enableLocalCache = enableLocalCache;
  }

  public int getExpiredTime() {
    return expiredTime;
  }

  public void setExpiredTime(int expiredTime) {
    this.expiredTime = expiredTime;
  }

  @Override
  public String toString() {
    return "MetadataServiceClientConfig{" +
        "poolsize=" + poolsize +
        ", server='" + server + '\'' +
        ", enableLocalCache=" + enableLocalCache +
        ", expiredTime=" + expiredTime +
        '}';
  }
}
