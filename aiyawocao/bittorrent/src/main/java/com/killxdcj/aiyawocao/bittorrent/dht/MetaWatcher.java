package com.killxdcj.aiyawocao.bittorrent.dht;

import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;

public interface MetaWatcher {
	void onGetInfoHash(BencodedString infohash);
	void onAnnouncePeer(BencodedString infohash, Peer peer);
}
