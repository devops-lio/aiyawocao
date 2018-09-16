package com.killxdcj.aiyawocao.bittorrent.dht;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.killxdcj.aiyawocao.bittorrent.utils.TimeUtils;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlackListManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(BlackListManager.class);
  private static final Object DUMMY_VALUE = new Object();

  private Cache<InetAddress, Object> blackHost;
  private LoadingCache<InetAddress, AtomicInteger> getPeersRecord;
  private int blackThreshold;

  public BlackListManager(int blackThreshold) {
    this.blackThreshold = blackThreshold;

    blackHost = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build();
    getPeersRecord = CacheBuilder.newBuilder()
        .expireAfterWrite(1, TimeUnit.MINUTES)
        .build(new CacheLoader<InetAddress, AtomicInteger>() {
          @Override
          public AtomicInteger load(InetAddress inetAddress) throws Exception {
            return new AtomicInteger(0);
          }
        });
  }

  public void shutdown() {
  }

  public boolean markGetPeers(InetAddress host) {
    int cnt = getPeersRecord.getUnchecked(host).incrementAndGet();
    if (cnt > blackThreshold) {
      LOGGER.info("add {} to black", host);
      blackHost.put(host, DUMMY_VALUE);
      return true;
    }
    return false;
  }

  public boolean isInBlack(InetAddress host) {
    return blackHost.getIfPresent(host) != null;
  }
}
