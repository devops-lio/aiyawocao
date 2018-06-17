package com.killxdcj.aiyawocao.bittorrent.dht;

import java.util.ArrayList;
import java.util.List;

public class NodeManager {
	private final long maxNeighbor;
	List<Node> allNode = new ArrayList<>();

	public NodeManager(long maxNeighbor) {
		this.maxNeighbor = maxNeighbor;
	}

	public List<Node> getAllNode() {
		List<Node> oldNodes = allNode;
		allNode = new ArrayList<>();
		return oldNodes;
	}

	public void putNode(Node node) {
		if (allNode.size() > maxNeighbor) {
			return;
		}
		allNode.add(node);
	}

	public List<Node> getPeers() {
		int size = allNode.size();
		if (size > 5) {
			return allNode.subList(0, 5);
		} else {
			return allNode.subList(0, size);
		}
	}
}
