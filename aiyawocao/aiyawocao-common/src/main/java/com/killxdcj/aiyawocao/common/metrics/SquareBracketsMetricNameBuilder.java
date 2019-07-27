package com.killxdcj.aiyawocao.common.metrics;

public class SquareBracketsMetricNameBuilder {
  public static SquareBracketsMetricNameBuilder newBuilder() {
    return new SquareBracketsMetricNameBuilder();
  }

  private StringBuffer sb = new StringBuffer();
  private transient boolean squareBracketsStart = false;

  public SquareBracketsMetricNameBuilder setName(String name) {
    sb.append(name);
    return this;
  }

  public SquareBracketsMetricNameBuilder addTagkv(String tagk, String tagv) {
    if (squareBracketsStart) {
     sb.append(",").append(tagk).append("=").append(tagv);
    } else {
      sb.append("{").append(tagk).append("=").append(tagv);
      squareBracketsStart = true;
    }
    return this;
  }

  public String build() {
    return sb.append("}").toString();
  }

  public static void main(String[] args) {
    System.out.println(SquareBracketsMetricNameBuilder.newBuilder()
        .setName("test.test.ste")
        .addTagkv("tag1", "tag2")
        .addTagkv("rag3", "tags2")
        .build());
  }
}
