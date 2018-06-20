package com.killxdcj.aiyawocao.bittorrent.config;

import java.util.ArrayList;
import java.util.List;

public class BittorrentConfig {
	private int port = 9613;
	private int maxPacketSize = 64 * 1024;
	private List<String> primeNodes = new ArrayList(){{
		add("router.bittorrent.com:6881");
		add("router.utorrent.com:6881");
		add("dht.transmissionbt.com:6881");
	}};
	private long transactionExpireTime = 5 * 60 * 1000;
	private int maxNeighbor = 3000;
	private long outBandwidthLimit = 1 * 1024 * 1024;
	private long requestLimit = 1500;

	public BittorrentConfig() {
	}

	public BittorrentConfig(int port, int maxPacketSize, List<String> primeNodes, long transactionExpireTime,
													int maxNeighbor, long outBandwidthLimit, long requestLimit) {
		this.port = port;
		this.maxPacketSize = maxPacketSize;
		this.primeNodes = primeNodes;
		this.transactionExpireTime = transactionExpireTime;
		this.maxNeighbor = maxNeighbor;
		this.outBandwidthLimit = outBandwidthLimit;
		this.requestLimit = requestLimit;
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

	public long getTransactionExpireTime() {
		return transactionExpireTime;
	}

	public void setTransactionExpireTime(long transactionExpireTime) {
		this.transactionExpireTime = transactionExpireTime;
	}

	public int getMaxNeighbor() {
		return maxNeighbor;
	}

	public void setMaxNeighbor(int maxNeighbor) {
		this.maxNeighbor = maxNeighbor;
	}

	public long getOutBandwidthLimit() {
		return outBandwidthLimit;
	}

	public void setOutBandwidthLimit(long outBandwidthLimit) {
		this.outBandwidthLimit = outBandwidthLimit;
	}

	public long getRequestLimit() {
		return requestLimit;
	}

	public void setRequestLimit(long requestLimit) {
		this.requestLimit = requestLimit;
	}

	@Override
	public String toString() {
		return "BittorrentConfig{" +
						"port=" + port +
						", maxPacketSize=" + maxPacketSize +
						", primeNodes=" + primeNodes +
						", transactionExpireTime=" + transactionExpireTime +
						", maxNeighbor=" + maxNeighbor +
						", outBandwidthLimit=" + outBandwidthLimit +
						", requestLimit=" + requestLimit +
						'}';
	}
}
