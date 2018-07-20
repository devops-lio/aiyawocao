package com.killxdcj.aiyawocao.bittorrent.metadata;

import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;

public class Task {
  private BencodedString infohash;
  private Peer peer;
  private MetadataListener listener;

  public Task(BencodedString infohash, Peer peer, MetadataListener listener) {
    this.infohash = infohash;
    this.peer = peer;
    this.listener = listener;
  }

  public BencodedString getInfohash() {
    return infohash;
  }

  public Peer getPeer() {
    return peer;
  }

  public MetadataListener getListener() {
    return listener;
  }

  @Override
  public int hashCode() {
    int result = infohash != null ? infohash.hashCode() : 0;
    result = 31 * result + (peer != null ? peer.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Task metadata = (Task) o;

    if (infohash != null ? !infohash.equals(metadata.infohash) : metadata.infohash != null)
      return false;
    return peer != null ? peer.equals(metadata.peer) : metadata.peer == null;
  }

  @Override
  public String toString() {
    return "Task{" + "infohash=" + infohash.asHexString() + ", peer=" + peer + '}';
  }
}
