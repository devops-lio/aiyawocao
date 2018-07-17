package com.killxdcj.aiyawocao.meta.crawler.config;

import com.killxdcj.aiyawocao.bittorrent.config.BittorrentConfig;
import com.killxdcj.aiyawocao.bittorrent.config.MetaFetchConfig;
import com.killxdcj.aiyawocao.meta.manager.config.MetaManagerConfig;
import com.killxdcj.aiyawocao.metadata.service.client.MetadataServiceClientConfig;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.yaml.snakeyaml.Yaml;

public class MetaCrawlerConfig {
  private long metaFetchTimeout = 5 * 60 * 1000;
  private int infohashMaxConcurrentFetch = 5;
  private int nodeMaxConcurrentFetch = 5;
  private String influxdbAddr = "example-influxdb:port";
  private String influxdbUser = "example-influxdb-user";
  private String influxdbPassword = "example-influxdb-user";
  private String influxdbName = "example-influxdb-name";
  private boolean useNIOMetaFetcher = true;
  private String cluster = "default";
  private boolean useProxyMetaManager = false;
  private BittorrentConfig bittorrentConfig;
  private MetaManagerConfig metaManagerConfig;
  private MetaFetchConfig metaFetchConfig;
  private MetadataServiceClientConfig metadataServiceClientConfig;

  public MetaCrawlerConfig() {
  }

  public static MetaCrawlerConfig fromYamlString(String yamlConf) {
    Yaml yaml = new Yaml();
    return yaml.loadAs(yamlConf, MetaCrawlerConfig.class);
  }

  public static MetaCrawlerConfig fromYamlConfFile(String confFile) throws FileNotFoundException {
    Yaml yaml = new Yaml();
    return yaml.loadAs(new FileInputStream(confFile), MetaCrawlerConfig.class);
  }

  public static void main(String[] args) {
    MetaCrawlerConfig metaCrawlerConfig = new MetaCrawlerConfig();
    metaCrawlerConfig.setBittorrentConfig(new BittorrentConfig());
    metaCrawlerConfig.setMetaManagerConfig(new MetaManagerConfig());
    metaCrawlerConfig.setMetaFetchConfig(new MetaFetchConfig());
    metaCrawlerConfig.setMetadataServiceClientConfig(new MetadataServiceClientConfig());
    String yamlConf = metaCrawlerConfig.toYamlConf();
    System.out.println(yamlConf);
    System.out.println(MetaCrawlerConfig.fromYamlString(yamlConf).toString());
  }

  public long getMetaFetchTimeout() {
    return metaFetchTimeout;
  }

  public void setMetaFetchTimeout(long metaFetchTimeout) {
    this.metaFetchTimeout = metaFetchTimeout;
  }

  public BittorrentConfig getBittorrentConfig() {
    return bittorrentConfig;
  }

  public void setBittorrentConfig(BittorrentConfig bittorrentConfig) {
    this.bittorrentConfig = bittorrentConfig;
  }

  public int getInfohashMaxConcurrentFetch() {
    return infohashMaxConcurrentFetch;
  }

  public void setInfohashMaxConcurrentFetch(int infohashMaxConcurrentFetch) {
    this.infohashMaxConcurrentFetch = infohashMaxConcurrentFetch;
  }

  public int getNodeMaxConcurrentFetch() {
    return nodeMaxConcurrentFetch;
  }

  public void setNodeMaxConcurrentFetch(int nodeMaxConcurrentFetch) {
    this.nodeMaxConcurrentFetch = nodeMaxConcurrentFetch;
  }

  public MetaManagerConfig getMetaManagerConfig() {
    return metaManagerConfig;
  }

  public void setMetaManagerConfig(MetaManagerConfig metaManagerConfig) {
    this.metaManagerConfig = metaManagerConfig;
  }

  public String getInfluxdbAddr() {
    return influxdbAddr;
  }

  public void setInfluxdbAddr(String influxdbAddr) {
    this.influxdbAddr = influxdbAddr;
  }

  public String getInfluxdbUser() {
    return influxdbUser;
  }

  public void setInfluxdbUser(String influxdbUser) {
    this.influxdbUser = influxdbUser;
  }

  public String getInfluxdbPassword() {
    return influxdbPassword;
  }

  public void setInfluxdbPassword(String influxdbPassword) {
    this.influxdbPassword = influxdbPassword;
  }

  public String getInfluxdbName() {
    return influxdbName;
  }

  public void setInfluxdbName(String influxdbName) {
    this.influxdbName = influxdbName;
  }

  public boolean getUseNIOMetaFetcher() {
    return useNIOMetaFetcher;
  }

  public void setUseNIOMetaFetcher(boolean useNIOMetaFetcher) {
    this.useNIOMetaFetcher = useNIOMetaFetcher;
  }

  public MetaFetchConfig getMetaFetchConfig() {
    return metaFetchConfig;
  }

  public void setMetaFetchConfig(MetaFetchConfig metaFetchConfig) {
    this.metaFetchConfig = metaFetchConfig;
  }

  public String getCluster() {
    return cluster;
  }

  public void setCluster(String cluster) {
    this.cluster = cluster;
  }

  public boolean getUseProxyMetaManager() {
    return useProxyMetaManager;
  }

  public void setUseProxyMetaManager(boolean useProxyMetaManager) {
    this.useProxyMetaManager = useProxyMetaManager;
  }

  public MetadataServiceClientConfig getMetadataServiceClientConfig() {
    return metadataServiceClientConfig;
  }

  public void setMetadataServiceClientConfig(MetadataServiceClientConfig metadataServiceClientConfig) {
    this.metadataServiceClientConfig = metadataServiceClientConfig;
  }

  @Override
  public String toString() {
    return "MetaCrawlerConfig{" +
        "metaFetchTimeout=" + metaFetchTimeout +
        ", infohashMaxConcurrentFetch=" + infohashMaxConcurrentFetch +
        ", nodeMaxConcurrentFetch=" + nodeMaxConcurrentFetch +
        ", influxdbAddr='" + influxdbAddr + '\'' +
        ", influxdbUser='" + influxdbUser + '\'' +
        ", influxdbPassword='" + influxdbPassword + '\'' +
        ", influxdbName='" + influxdbName + '\'' +
        ", useNIOMetaFetcher=" + useNIOMetaFetcher +
        ", cluster='" + cluster + '\'' +
        ", useProxyMetaManager=" + useProxyMetaManager +
        ", bittorrentConfig=" + bittorrentConfig +
        ", metaManagerConfig=" + metaManagerConfig +
        ", metaFetchConfig=" + metaFetchConfig +
        ", metadataServiceClientConfig=" + metadataServiceClientConfig +
        '}';
  }

  public String toYamlConf() {
    Yaml yaml = new Yaml();
    return yaml.dumpAsMap(this);
  }
}
