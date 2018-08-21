package com.killxdcj.aiyawocao.bittorrent.dht;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class NodeManager {

  BlockingQueue<Node> neighborQueue;

  public NodeManager(int maxNeighbor) {
    this.neighborQueue = new LinkedBlockingQueue<>(maxNeighbor);
  }

  public boolean putNode(Node node) {
    return neighborQueue.offer(node);
  }

  public Node getNode() throws InterruptedException {
    return neighborQueue.poll(5, TimeUnit.SECONDS);
  }

  public List<Node> getPeers() {
    List<Node> ret = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      Node node = neighborQueue.peek();
      if (node != null) {
        ret.add(node);
      }
    }

    return ret;
  }
}
