package com.killxdcj.aiyawocao.bittorrent.peer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class NodeFetchersManager {
  private volatile boolean waiting = true;
  private List<MetaFetcher> fetchers = new ArrayList<>();
  private HashMap<MetaFetcher, Object> fetcherMap = new HashMap<>();
  private int failed = 0;

  public synchronized boolean add(MetaFetcher fetcher) {
    if (!fetcherMap.containsKey(fetcher)) {
      fetchers.add(fetcher);
    }

    if (waiting) {
      waiting = false;
      return true;
    } else {
      return false;
    }
  }

  public synchronized MetaFetcher get() {
    if (fetchers.size() == 0) {
      waiting = true;
      return null;
    }

    MetaFetcher fetcher = fetchers.remove(0);
    if (fetcher != null) {
      fetcherMap.remove(fetcher);
    }
    return fetcher;
  }

  public synchronized int updateResult(boolean successed) {
    if (successed) {
      failed = 0;
    } else {
      failed++;
    }
    return failed;
  }

  public synchronized void clean() {
    waiting = true;
    fetchers.clear();
    fetcherMap.clear();
  }
}
