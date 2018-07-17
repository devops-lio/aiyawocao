package com.killxdcj.aiyawocao.bittorrent.peer;

import java.net.InetAddress;

public class Peer {
  protected InetAddress addr;
  protected int port;

  public Peer(InetAddress addr, int port) {
    this.addr = addr;
    this.port = port;
  }

  public InetAddress getAddr() {
    return addr;
  }

  public void setAddr(InetAddress addr) {
    this.addr = addr;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  @Override
  public int hashCode() {
    int result = addr != null ? addr.hashCode() : 0;
    result = 31 * result + port;
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Peer peer = (Peer) o;

    if (port != peer.port) return false;
    return addr != null ? addr.equals(peer.addr) : peer.addr == null;
  }

  @Override
  public String toString() {
    return "" + addr.getHostAddress() + ":" + port;
  }
}
