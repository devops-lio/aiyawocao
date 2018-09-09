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

  private static final Object DUMMY_OBJ = new Object();

  private Cache<InetAddress, Object> blackHost;
  private ConcurrentMap<InetAddress, AtomicInteger> getpeersRecord = new ConcurrentHashMap<>();
  private int blackThreshold;
  private volatile boolean exit = false;
  private Thread blackListCalcProc;

  public BlackListManager(int blackThreshold) {
    this.blackThreshold = blackThreshold;

    blackHost = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .build();
    blackListCalcProc = new Thread(this::calcBlackListProc);
    blackListCalcProc.start();
  }

  public void shutdown() {
    exit = true;
    blackListCalcProc.interrupt();
  }

  public void markGetPeers(InetAddress host) {
    getpeersRecord.computeIfAbsent(host, bytes -> new AtomicInteger(0)).incrementAndGet();
  }

  public boolean isInBlack(InetAddress host) {
    return blackHost.getIfPresent(host) != null;
  }

  private void calcBlackListProc() {
    Thread.currentThread().setName("BlackListManager Blacklist Calc Proc");
    while (!exit) {
      try {
        Thread.sleep(60 * 1000);

        long cur = TimeUtils.getCurTime();
        ConcurrentMap<InetAddress, AtomicInteger> oldGetpeersCnt = getpeersRecord;
        getpeersRecord = new ConcurrentHashMap<>();

        List<String> newBlackList = new ArrayList<>();
        StringBuilder sbBlk = new StringBuilder();
        for (Map.Entry<InetAddress, AtomicInteger> entry : oldGetpeersCnt.entrySet()) {
          if (entry.getValue().get() > blackThreshold) {
            blackHost.put(entry.getKey(), DUMMY_OBJ);
            sbBlk.append(" " + entry.getKey() + "->" + entry.getValue().get());
          }
        }

        LOGGER.info(
            "BlackListManager Blacklist Calc Proc, costtime:{}, total:{}, black:{}, blks:{}",
            TimeUtils.getElapseTime(cur),
            oldGetpeersCnt.size(),
            newBlackList.size(),
            sbBlk.toString());
      } catch (InterruptedException e) {
      } catch (Throwable t) {
        LOGGER.error("BlackListManager proc error", t);
      }
    }
  }
}
