package com.killxdcj.aiyawocao.bittorrent.config;

public class MetaFetchConfig {
  private int blackThreshold = 3;
  private int blackTimeMs = 60 * 60 * 1000;
  private int metafetchTimeoutMs = 10 * 60 * 1000;
  private int fetcherNum = 20;
  private int infohashMaxPending = 3;

  public MetaFetchConfig() {
  }

  public int getBlackThreshold() {
    return blackThreshold;
  }

  public void setBlackThreshold(int blackThreshold) {
    this.blackThreshold = blackThreshold;
  }

  public int getBlackTimeMs() {
    return blackTimeMs;
  }

  public void setBlackTimeMs(int blackTimeMs) {
    this.blackTimeMs = blackTimeMs;
  }

  public int getMetafetchTimeoutMs() {
    return metafetchTimeoutMs;
  }

  public void setMetafetchTimeoutMs(int metafetchTimeoutMs) {
    this.metafetchTimeoutMs = metafetchTimeoutMs;
  }

  public int getFetcherNum() {
    return fetcherNum;
  }

  public void setFetcherNum(int fetcherNum) {
    this.fetcherNum = fetcherNum;
  }

  public int getInfohashMaxPending() {
    return infohashMaxPending;
  }

  public void setInfohashMaxPending(int infohashMaxPending) {
    this.infohashMaxPending = infohashMaxPending;
  }

  @Override
  public String toString() {
    return "MetaFetchConfig{" +
        "blackThreshold=" + blackThreshold +
        ", blackTimeMs=" + blackTimeMs +
        ", metafetchTimeoutMs=" + metafetchTimeoutMs +
        ", fetcherNum=" + fetcherNum +
        ", infohashMaxPending=" + infohashMaxPending +
        '}';
  }
}
