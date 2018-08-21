package com.killxdcj.aiyawocao.bittorrent.dht;

import com.killxdcj.aiyawocao.bittorrent.utils.TimeUtils;

public class Transaction {

  Node node;
  KRPC krpc;
  long expiredTime;

  public Transaction(Node node, KRPC krpc, long expiredTime) {
    this.node = node;
    this.krpc = krpc;
    this.expiredTime = TimeUtils.getExpiredTime(expiredTime);
  }

  public Node getNode() {
    return node;
  }

  public void setNode(Node node) {
    this.node = node;
  }

  public KRPC getKrpc() {
    return krpc;
  }

  public void setKrpc(KRPC krpc) {
    this.krpc = krpc;
  }

  public boolean isExpired() {
    if (TimeUtils.getCurTime() > expiredTime) {
      return true;
    }
    return false;
  }
}
