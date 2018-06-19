package com.killxdcj.aiyawocao.bittorrent.dht;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class NodeManager {
	BlockingQueue<Node> neighborQueue;

	public NodeManager(int maxNeighbor) {
		this.neighborQueue = new LinkedBlockingQueue<>(maxNeighbor);
	}

	public void putNode(Node node) {
		neighborQueue.offer(node);
	}

	public Node getNode() throws InterruptedException {
		return neighborQueue.poll(5, TimeUnit.SECONDS);
	}

	public List<Node> getPeers() {
		return Collections.emptyList();
	}
}
