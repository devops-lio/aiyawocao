package com.killxdcj.aiyawocao.meta.manager;

import com.killxdcj.aiyawocao.meta.manager.exception.InvalidInfohashException;
import com.killxdcj.aiyawocao.meta.manager.exception.MetaNotExistException;

import java.io.IOException;

public interface MetaManager {
	void shutdown();
	boolean doesMetaExist(String infohash);
	boolean doesMetaExist(String infohash, boolean forceCheckOSS);
	void put(String infohash, byte[] meta);
	byte[] get(String infohash) throws MetaNotExistException, IOException;
}
