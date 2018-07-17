package com.killxdcj.aiyawocao.common.metrics;

public class InfluxdbBackendMetricsConfig {
  private String influxdbAddr = "example-influxdb:port";
  private String influxdbUser = "example-influxdb-user";
  private String influxdbPassword = "example-influxdb-user";
  private String influxdbName = "example-influxdb-name";
  private String cluster = "default";
  private int reportPeriod = 60;

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

  public String getCluster() {
    return cluster;
  }

  public void setCluster(String cluster) {
    this.cluster = cluster;
  }

  public int getReportPeriod() {
    return reportPeriod;
  }

  public void setReportPeriod(int reportPeriod) {
    this.reportPeriod = reportPeriod;
  }

  @Override
  public String toString() {
    return "InfluxdbBackendMetricsConfig{" +
        "influxdbAddr='" + influxdbAddr + '\'' +
        ", influxdbUser='" + influxdbUser + '\'' +
        ", influxdbPassword='" + influxdbPassword + '\'' +
        ", influxdbName='" + influxdbName + '\'' +
        ", cluster='" + cluster + '\'' +
        ", reportPeriod=" + reportPeriod +
        '}';
  }
}
