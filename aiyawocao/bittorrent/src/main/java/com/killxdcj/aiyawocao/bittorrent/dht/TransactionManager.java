package com.killxdcj.aiyawocao.bittorrent.dht;

import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.utils.JTorrentUtils;
import com.killxdcj.aiyawocao.bittorrent.utils.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Created with IntelliJ IDEA. User: caojianhua Date: 2017/04/04 Time: 16:38 */
public class TransactionManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(TransactionManager.class);

  private static final int TRANSACTION_EXPIRED_CHECK_PERIOD = 1 * 60 * 1000;

  private Map<BencodedString, Transaction> transactionTable = new ConcurrentHashMap<>();
  private ScheduledExecutorService scheduledExecutorService =
      Executors.newScheduledThreadPool(
          5,
          r -> {
            Thread thread = new Thread(r, "TransactionManager schedule");
            thread.setDaemon(true);
            return thread;
          });

  public TransactionManager() {
    startTransactionExpiredCheckSchedule();
  }

  public static BencodedString genTransactionId() {
    return new BencodedString(JTorrentUtils.genByte(2));
  }

  public void shutdown() {
    scheduledExecutorService.shutdown();
  }

  private void startTransactionExpiredCheckSchedule() {
    scheduledExecutorService.scheduleAtFixedRate(
        () -> {
          long startTime = TimeUtils.getCurTime();

          List<BencodedString> expiredTrans = new ArrayList<>();
          for (Map.Entry<BencodedString, Transaction> entry : transactionTable.entrySet()) {
            if (entry.getValue().isExpired()) {
              expiredTrans.add(entry.getKey());
            }
          }

          for (BencodedString key : expiredTrans) {
            transactionTable.remove(key);
          }

          LOGGER.info(
              "TransactionExpiredCheckSchedule end, expired:{} costtime: {} ms",
              expiredTrans.size(),
              TimeUtils.getElapseTime(startTime));
        },
        0,
        TRANSACTION_EXPIRED_CHECK_PERIOD,
        TimeUnit.MILLISECONDS);
  }

  public void putTransaction(Transaction transaction) {
    transactionTable.put(transaction.getKrpc().getTransId(), transaction);
  }

  public Transaction getTransaction(BencodedString transId) {
    return transactionTable.remove(transId);
  }
}
