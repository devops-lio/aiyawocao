package com.killxdcj.aiyawocao.bittorrent.metadata;

import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerTaskManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(PeerTaskManager.class);

  private ConcurrentMap<Peer, List<Task>> peerTasks = new ConcurrentHashMap<>();

  public boolean submitTask(Task task) {
    boolean isNewPeer = false;
    synchronized (peerTasks) {
      if (!peerTasks.containsKey(task.getPeer())) {
        isNewPeer = true;
        peerTasks.put(task.getPeer(), new ArrayList<>());
      }
      peerTasks.get(task.getPeer()).add(task);
      return isNewPeer;
    }
  }

  public Task getNextTask(Peer peer) {
    synchronized (peerTasks) {
      List<Task> tasks = peerTasks.get(peer);
      if (tasks.size() == 0) {
        peerTasks.remove(peer);
        return null;
      } else {
        if (tasks.size() > 10) {
          LOGGER.info("pending fetch task size {} > 10, {}:{}", tasks.size(), peer.getAddr(), peer.getPort());
        }
        return tasks.remove(0);
      }
    }
  }

  public int getPending() {
    int pending = 0;
    for (List<Task> tasks : peerTasks.values()) {
      pending += tasks.size();
    }
    return pending;
  }

  public int getRunning() {
    return peerTasks.size();
  }
}
