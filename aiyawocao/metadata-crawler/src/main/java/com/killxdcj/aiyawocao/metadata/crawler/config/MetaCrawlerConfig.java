package com.killxdcj.aiyawocao.metadata.crawler.config;

import com.killxdcj.aiyawocao.bittorrent.config.BittorrentConfig;
import com.killxdcj.aiyawocao.common.metrics.InfluxdbBackendMetricsConfig;
import com.killxdcj.aiyawocao.metadata.service.client.MetadataServiceClientConfig;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import org.yaml.snakeyaml.Yaml;

public class MetaCrawlerConfig {

  private int maxPendingAnnouncePeerReq = 10000;
  private int fetcherSubmitterNum = 20;
  private int maxPendingPutMetadata = 1000;
  private int metadataPutterNum = 20;
  private BittorrentConfig bittorrentConfig;
  private MetadataServiceClientConfig metadataServiceClientConfig;
  private InfluxdbBackendMetricsConfig influxdbBackendMetricsConfig;

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
    metaCrawlerConfig.setInfluxdbBackendMetricsConfig(new InfluxdbBackendMetricsConfig());
    metaCrawlerConfig.setMetadataServiceClientConfig(new MetadataServiceClientConfig());
    String yamlConf = metaCrawlerConfig.toYamlConf();
    System.out.println(yamlConf);
    System.out.println(MetaCrawlerConfig.fromYamlString(yamlConf).toString());
  }

  public BittorrentConfig getBittorrentConfig() {
    return bittorrentConfig;
  }

  public void setBittorrentConfig(BittorrentConfig bittorrentConfig) {
    this.bittorrentConfig = bittorrentConfig;
  }

  public MetadataServiceClientConfig getMetadataServiceClientConfig() {
    return metadataServiceClientConfig;
  }

  public void setMetadataServiceClientConfig(
      MetadataServiceClientConfig metadataServiceClientConfig) {
    this.metadataServiceClientConfig = metadataServiceClientConfig;
  }

  public InfluxdbBackendMetricsConfig getInfluxdbBackendMetricsConfig() {
    return influxdbBackendMetricsConfig;
  }

  public void setInfluxdbBackendMetricsConfig(
      InfluxdbBackendMetricsConfig influxdbBackendMetricsConfig) {
    this.influxdbBackendMetricsConfig = influxdbBackendMetricsConfig;
  }

  public int getMaxPendingAnnouncePeerReq() {
    return maxPendingAnnouncePeerReq;
  }

  public void setMaxPendingAnnouncePeerReq(int maxPendingAnnouncePeerReq) {
    this.maxPendingAnnouncePeerReq = maxPendingAnnouncePeerReq;
  }

  public int getFetcherSubmitterNum() {
    return fetcherSubmitterNum;
  }

  public void setFetcherSubmitterNum(int fetcherSubmitterNum) {
    this.fetcherSubmitterNum = fetcherSubmitterNum;
  }

  public int getMaxPendingPutMetadata() {
    return maxPendingPutMetadata;
  }

  public void setMaxPendingPutMetadata(int maxPendingPutMetadata) {
    this.maxPendingPutMetadata = maxPendingPutMetadata;
  }

  public int getMetadataPutterNum() {
    return metadataPutterNum;
  }

  public void setMetadataPutterNum(int metadataPutterNum) {
    this.metadataPutterNum = metadataPutterNum;
  }

  @Override
  public String toString() {
    return "MetaCrawlerConfig{" +
        "maxPendingAnnouncePeerReq=" + maxPendingAnnouncePeerReq +
        ", fetcherSubmitterNum=" + fetcherSubmitterNum +
        ", maxPendingPutMetadata=" + maxPendingPutMetadata +
        ", metadataPutterNum=" + metadataPutterNum +
        ", bittorrentConfig=" + bittorrentConfig +
        ", metadataServiceClientConfig=" + metadataServiceClientConfig +
        ", influxdbBackendMetricsConfig=" + influxdbBackendMetricsConfig +
        '}';
  }

  public String toYamlConf() {
    Yaml yaml = new Yaml();
    return yaml.dumpAsMap(this);
  }
}
