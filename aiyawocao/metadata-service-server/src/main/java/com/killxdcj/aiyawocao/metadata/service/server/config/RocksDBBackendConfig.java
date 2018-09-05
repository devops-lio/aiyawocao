package com.killxdcj.aiyawocao.metadata.service.server.config;

public class RocksDBBackendConfig {

  private String rocksDBPath = "/data/aiyawocao/metadata/index";
  private String originalMetadataPath = "/data/aiyawocao/metadata/original";
  private boolean enableBitfilter = true;

  public String getRocksDBPath() {
    return rocksDBPath;
  }

  public void setRocksDBPath(String rocksDBPath) {
    this.rocksDBPath = rocksDBPath;
  }

  public String getOriginalMetadataPath() {
    return originalMetadataPath;
  }

  public void setOriginalMetadataPath(String originalMetadataPath) {
    this.originalMetadataPath = originalMetadataPath;
  }

  public boolean getEnableBitfilter() {
    return enableBitfilter;
  }

  public void setEnableBitfilter(boolean enableBitfilter) {
    this.enableBitfilter = enableBitfilter;
  }

  @Override
  public String toString() {
    return "RocksDBBackendConfig{" +
        "rocksDBPath='" + rocksDBPath + '\'' +
        ", originalMetadataPath='" + originalMetadataPath + '\'' +
        ", enableBitfilter=" + enableBitfilter +
        '}';
  }
}
