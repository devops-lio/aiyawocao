package com.killxdcj.aiyawocao.bittorrent.peer;

import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;

public interface MetaFetchWatcher {
	void onSuccessed(BencodedString infohash, Peer peer, byte[] metadata, long costtime);
	void onException(BencodedString infohash, Peer peer, Throwable t, long costtime);
}
