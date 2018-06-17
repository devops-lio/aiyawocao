package com.killxdcj.aiyawocao.bittorrent.dht;

import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.utils.TimeUtils;

import java.net.InetAddress;

public class Node {
	BencodedString id;
	int port;
	long lastActive;
	private InetAddress addr;

	public Node(InetAddress addr, int port) {
		this.addr = addr;
		this.port = port;
	}

	public Node(BencodedString id, InetAddress addr, int port) {
		this.id = id;
		this.addr = addr;
		this.port = port;
		this.lastActive = TimeUtils.getCurTime();
	}

	public BencodedString getId() {
		return id;
	}

	public void setId(BencodedString id) {
		this.id = id;
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

	public long getLastActive() {
		return lastActive;
	}

	public void setLastActive(long lastActive) {
		this.lastActive = lastActive;
	}

	@Override
	public String toString() {
		return "Node{" +
						"id=" + id +
						", addr=" + addr +
						", port=" + port +
						", lastActive=" + lastActive +
						'}';
	}
}
