package com.killxdcj.aiyawocao.bittorrent.metadata;

import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;

public interface MetadataListener {
	void onSuccedded(Peer peer, BencodedString infohash, byte[] metadata);
	void onFailed(Peer peer, BencodedString infohash, Throwable t);
}
