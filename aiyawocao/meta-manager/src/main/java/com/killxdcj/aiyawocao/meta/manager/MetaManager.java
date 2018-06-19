package com.killxdcj.aiyawocao.meta.manager;

import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.meta.manager.exception.MetaNotExistException;

import java.io.IOException;

public abstract class MetaManager {
	protected MetricRegistry metricRegistry;

	public MetaManager(MetricRegistry metricRegistry) {
		this.metricRegistry = metricRegistry;
	}

	public abstract void shutdown();
	public abstract boolean doesMetaExist(String infohash);
	public abstract boolean doesMetaExist(String infohash, boolean forceCheckOSS);
	public abstract void put(String infohash, byte[] meta);
	public abstract byte[] get(String infohash) throws MetaNotExistException, IOException;
}
