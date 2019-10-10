package com.killxdcj.aiyawocao.web.model;

public class FriendLink {
  private String name;
  private String url;

  public FriendLink(String name, String url) {
    this.name = name;
    this.url = url;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  @Override
  public String toString() {
    return "FriendLink{" +
        "name='" + name + '\'' +
        ", url='" + url + '\'' +
        '}';
  }
}
