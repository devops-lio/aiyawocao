package com.killxdcj.aiyawocao.metadata.indexer.config;

public class ESIndexerConfig {
  private String hostname = "example.com";
  private int port = 9999;
  private String index = "example.index";
  private long timeoutS = 60;

  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = hostname;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getIndex() {
    return index;
  }

  public void setIndex(String index) {
    this.index = index;
  }

  public long getTimeoutS() {
    return timeoutS;
  }

  public void setTimeoutS(long timeoutS) {
    this.timeoutS = timeoutS;
  }

  @Override
  public String toString() {
    return "ESIndexerConfig{" +
        "hostname='" + hostname + '\'' +
        ", port=" + port +
        ", index='" + index + '\'' +
        ", timeoutS=" + timeoutS +
        '}';
  }
}
