package com.killxdcj.aiyawocao.meta.manager.config;

import org.yaml.snakeyaml.Yaml;

public class MetaManagerConfig {
  private String bucketName = "bittorrent-meta";
  private String indexRoot = "index";
  private String indexPrefix = "index";
  private String infohashMetaKey = "infohash-meta";
  private String endpoint = "http://oss-cn-shenzhen.aliyuncs.com";
  private String accessKeyId = "example-accesskey-id";
  private String accessKeySecret = "example-accesskey-secret";
  private String metaCentreAddr = "127.0.0.1:10241";
  private int maxIndexSize = 150000;

  public MetaManagerConfig() {
  }

  public static MetaManagerConfig fromYamlConf(String yamlConf) {
    Yaml yaml = new Yaml();
    return yaml.loadAs(yamlConf, MetaManagerConfig.class);
  }

  public static void main(String[] args) {
    MetaManagerConfig metaManagerConfig = new MetaManagerConfig();
    String yamlConf = metaManagerConfig.toYamlConf();
    System.out.println(yamlConf);
    System.out.println(MetaManagerConfig.fromYamlConf(yamlConf).toString());
  }

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

  public String getInfohashMetaKey() {
    return infohashMetaKey;
  }

  public void setInfohashMetaKey(String infohashMetaKey) {
    this.infohashMetaKey = infohashMetaKey;
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

  public String getMetaCentreAddr() {
    return metaCentreAddr;
  }

  public void setMetaCentreAddr(String metaCentreAddr) {
    this.metaCentreAddr = metaCentreAddr;
  }

  public int getMaxIndexSize() {
    return maxIndexSize;
  }

  public void setMaxIndexSize(int maxIndexSize) {
    this.maxIndexSize = maxIndexSize;
  }

  @Override
  public String toString() {
    return "MetaManagerConfig{" +
        "bucketName='" + bucketName + '\'' +
        ", indexRoot='" + indexRoot + '\'' +
        ", indexPrefix='" + indexPrefix + '\'' +
        ", infohashMetaKey='" + infohashMetaKey + '\'' +
        ", endpoint='" + endpoint + '\'' +
        ", accessKeyId='" + accessKeyId + '\'' +
        ", accessKeySecret='" + accessKeySecret + '\'' +
        ", metaCentreAddr='" + metaCentreAddr + '\'' +
        '}';
  }

  public String toYamlConf() {
    Yaml yaml = new Yaml();
    return yaml.dumpAsMap(this);
  }
}
