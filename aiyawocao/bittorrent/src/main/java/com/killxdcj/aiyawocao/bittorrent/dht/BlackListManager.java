package com.killxdcj.aiyawocao.bittorrent.dht;

import com.killxdcj.aiyawocao.bittorrent.utils.TimeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlackListManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(BlackListManager.class);

  private ConcurrentSkipListSet<String> blackList = new ConcurrentSkipListSet<>();
  private ConcurrentMap<String, AtomicInteger> getpeersCnt = new ConcurrentHashMap<>();
  private int blackThreshold;
  private volatile boolean exit = false;
  private Thread blackListCalcProc;

  public BlackListManager(int blackThreshold) {
    this.blackThreshold = blackThreshold;
    blackListCalcProc = new Thread(this::calcBlackListProc);
    blackListCalcProc.start();
  }

  public void shutdown() {
    exit = true;
    blackListCalcProc.interrupt();
  }

  public void mark(String addr) {
    getpeersCnt.computeIfAbsent(addr, nodeaddr -> new AtomicInteger(0)).incrementAndGet();
  }

  public boolean isInBlackList(String addr) {
    return blackList.contains(addr);
  }

  private void calcBlackListProc() {
    Thread.currentThread().setName("BlackListManager Blacklist Calc Proc");
    while (!exit) {
      try {
        Thread.sleep(60 * 1000);

        long cur = TimeUtils.getCurTime();
        ConcurrentMap<String, AtomicInteger> oldGetpeersCnt = getpeersCnt;
        getpeersCnt = new ConcurrentHashMap<>();

        List<String> newBlackList = new ArrayList<>();
        StringBuilder sbBlk = new StringBuilder();
        for (Map.Entry<String, AtomicInteger> entry : oldGetpeersCnt.entrySet()) {
          if (entry.getValue().get() > blackThreshold) {
            newBlackList.add(entry.getKey());
            sbBlk.append(" " + entry.getKey() + "->" + entry.getValue().get());
          }
        }

        blackList.addAll(newBlackList);
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
