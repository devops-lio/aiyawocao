package com.killxdcj.aiyawocao.bittorrent.config;

import java.util.ArrayList;
import java.util.List;

public class BittorrentConfig {
	private int port = 9613;
	private int maxPacketSize = 10 * 1024 * 1024;
	private List<String> primeNodes = new ArrayList(){{
		add("router.bittorrent.com:6881");
		add("router.utorrent.com:6881");
		add("dht.transmissionbt.com:6881");
	}};
	private long findNodeInterval = 1000;
	private long transactionExpireTime = 10 * 60 * 1000;
	private long maxNeighbor = 2000;

	public BittorrentConfig() {
	}

	public BittorrentConfig(int port, int maxPacketSize, List<String> primeNodes, long findNodeInterval,
													long transactionExpireTime, long maxNeighbor) {
		this.port = port;
		this.maxPacketSize = maxPacketSize;
		this.primeNodes = primeNodes;
		this.findNodeInterval = findNodeInterval;
		this.transactionExpireTime = transactionExpireTime;
		this.maxNeighbor = maxNeighbor;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getMaxPacketSize() {
		return maxPacketSize;
	}

	public void setMaxPacketSize(int maxPacketSize) {
		this.maxPacketSize = maxPacketSize;
	}

	public List<String> getPrimeNodes() {
		return primeNodes;
	}

	public void setPrimeNodes(List<String> primeNodes) {
		this.primeNodes = primeNodes;
	}

	public long getFindNodeInterval() {
		return findNodeInterval;
	}

	public void setFindNodeInterval(long findNodeInterval) {
		this.findNodeInterval = findNodeInterval;
	}

	public long getTransactionExpireTime() {
		return transactionExpireTime;
	}

	public void setTransactionExpireTime(long transactionExpireTime) {
		this.transactionExpireTime = transactionExpireTime;
	}

	public long getMaxNeighbor() {
		return maxNeighbor;
	}

	public void setMaxNeighbor(long maxNeighbor) {
		this.maxNeighbor = maxNeighbor;
	}
}
