package com.killxdcj.aiyawocao.metadata.service.client;

public class MetadataServiceClientConfig {
  private int poolsize = 10;
  private String server = "btproxy.host:10242";

  public int getPoolsize() {
    return poolsize;
  }

  public void setPoolsize(int poolsize) {
    this.poolsize = poolsize;
  }

  public String getServer() {
    return server;
  }

  public void setServer(String server) {
    this.server = server;
  }

  @Override
  public String toString() {
    return "MetadataServiceClientConfig{"
        + "poolsize="
        + poolsize
        + ", server='"
        + server
        + '\''
        + '}';
  }
}
