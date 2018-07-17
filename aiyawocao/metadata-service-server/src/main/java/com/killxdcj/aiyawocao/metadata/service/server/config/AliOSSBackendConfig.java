package com.killxdcj.aiyawocao.metadata.service.server.config;

public class AliOSSBackendConfig {
  private String bucketName = "bittorrent-meta";
  private String indexRoot = "index";
  private String indexPrefix = "index";
  private String endpoint = "http://oss-cn-shenzhen.aliyuncs.com";
  private String accessKeyId = "example-accesskey-id";
  private String accessKeySecret = "example-accesskey-secret";
  private int maxIndexSize = 150000;
  private long indexSaveInterval = 10 * 60 * 1000;

  public String getBucketName() {
    return bucketName;
  }

  public void setBucketName(String bucketName) {
    this.bucketName = bucketName;
  }

  public String getIndexRoot() {
    return indexRoot;
  }

  public void setIndexRoot(String indexRoot) {
    this.indexRoot = indexRoot;
  }

  public String getIndexPrefix() {
    return indexPrefix;
  }

  public void setIndexPrefix(String indexPrefix) {
    this.indexPrefix = indexPrefix;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getAccessKeyId() {
    return accessKeyId;
  }

  public void setAccessKeyId(String accessKeyId) {
    this.accessKeyId = accessKeyId;
  }

  public String getAccessKeySecret() {
    return accessKeySecret;
  }

  public void setAccessKeySecret(String accessKeySecret) {
    this.accessKeySecret = accessKeySecret;
  }

  public int getMaxIndexSize() {
    return maxIndexSize;
  }

  public void setMaxIndexSize(int maxIndexSize) {
    this.maxIndexSize = maxIndexSize;
  }

  public long getIndexSaveInterval() {
    return indexSaveInterval;
  }

  public void setIndexSaveInterval(long indexSaveInterval) {
    this.indexSaveInterval = indexSaveInterval;
  }

  @Override
  public String toString() {
    return "AliOSSBackendConfig{" +
        "bucketName='" + bucketName + '\'' +
        ", indexRoot='" + indexRoot + '\'' +
        ", indexPrefix='" + indexPrefix + '\'' +
        ", endpoint='" + endpoint + '\'' +
        ", accessKeyId='" + accessKeyId + '\'' +
        ", accessKeySecret='" + accessKeySecret + '\'' +
        ", maxIndexSize=" + maxIndexSize +
        ", indexSaveInterval=" + indexSaveInterval +
        '}';
  }
}
