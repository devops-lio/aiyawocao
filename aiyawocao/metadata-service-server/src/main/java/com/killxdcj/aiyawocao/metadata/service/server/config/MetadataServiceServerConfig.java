package com.killxdcj.aiyawocao.metadata.service.server.config;

import com.killxdcj.aiyawocao.common.metrics.InfluxdbBackendMetricsConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class MetadataServiceServerConfig {
  private int port = 9613;
  private int executorThreadNum = 50;
  private AliOSSBackendConfig aliOSSBackendConfig;
  private InfluxdbBackendMetricsConfig influxdbBackendMetricsConfig;

  public static MetadataServiceServerConfig fromYamlConfFile(String confFile)
      throws FileNotFoundException {
    Yaml yaml = new Yaml();
    return yaml.loadAs(new FileInputStream(new File(confFile)), MetadataServiceServerConfig.class);
  }

  public static MetadataServiceServerConfig fromYamlString(String yamlString) {
    Yaml yaml = new Yaml();
    return yaml.loadAs(yamlString, MetadataServiceServerConfig.class);
  }

  public static void main(String[] args) {
    MetadataServiceServerConfig config = new MetadataServiceServerConfig();
    config.setAliOSSBackendConfig(new AliOSSBackendConfig());
    config.setInfluxdbBackendMetricsConfig(new InfluxdbBackendMetricsConfig());
    String yamlString = config.toYamlString();
    System.out.println(yamlString);
    System.out.println(MetadataServiceServerConfig.fromYamlString(yamlString).toString());
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public AliOSSBackendConfig getAliOSSBackendConfig() {
    return aliOSSBackendConfig;
  }

  public void setAliOSSBackendConfig(AliOSSBackendConfig aliOSSBackendConfig) {
    this.aliOSSBackendConfig = aliOSSBackendConfig;
  }

  public int getExecutorThreadNum() {
    return executorThreadNum;
  }

  public void setExecutorThreadNum(int executorThreadNum) {
    this.executorThreadNum = executorThreadNum;
  }

  public InfluxdbBackendMetricsConfig getInfluxdbBackendMetricsConfig() {
    return influxdbBackendMetricsConfig;
  }

  public void setInfluxdbBackendMetricsConfig(
      InfluxdbBackendMetricsConfig influxdbBackendMetricsConfig) {
    this.influxdbBackendMetricsConfig = influxdbBackendMetricsConfig;
  }

  @Override
  public String toString() {
    return "MetadataServiceServerConfig{"
        + "port="
        + port
        + ", executorThreadNum="
        + executorThreadNum
        + ", aliOSSBackendConfig="
        + aliOSSBackendConfig
        + '}';
  }

  private String toYamlString() {
    Yaml yaml = new Yaml();
    return yaml.dumpAsMap(this);
  }
}
