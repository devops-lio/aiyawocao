package com.killxdcj.aiyawocao.metadata.service.server.config;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class MetadataServiceServerConfig {
  private int port = 9613;
  AliOSSBackendConfig aliOSSBackendConfig;

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

  @Override
  public String toString() {
    return "MetadataServiceServerConfig{" +
      "port=" + port +
      ", aliOSSBackendConfig=" + aliOSSBackendConfig +
      '}';
  }

  private String toYamlString() {
    Yaml yaml = new Yaml();
    return yaml.dumpAsMap(this);
  }

  public static MetadataServiceServerConfig fromYamlConfFile(String confFile) throws FileNotFoundException {
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
    String yamlString = config.toYamlString();
    System.out.println(yamlString);
    System.out.println(MetadataServiceServerConfig.fromYamlString(yamlString).toString());
  }
}
