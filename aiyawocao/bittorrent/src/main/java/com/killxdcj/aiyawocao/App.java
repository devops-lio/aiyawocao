package com.killxdcj.aiyawocao;

import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.config.BittorrentConfig;
import com.killxdcj.aiyawocao.bittorrent.dht.DHT;
import com.killxdcj.aiyawocao.bittorrent.dht.MetaWatcher;
import com.killxdcj.aiyawocao.bittorrent.exception.InvalidBittorrentPacketException;
import com.killxdcj.aiyawocao.bittorrent.metadata.MetadataFetcher;
import com.killxdcj.aiyawocao.bittorrent.metadata.MetadataListener;
import com.killxdcj.aiyawocao.bittorrent.peer.MetaFetchWatcher;
import com.killxdcj.aiyawocao.bittorrent.peer.MetaFetcher;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

/** Hello world! */
public class App {
  public static void main(String[] args)
      throws SocketException, InterruptedException, DecoderException, UnknownHostException {
    //        testDHT();
    testNewFetcher();
    while (true) {
      Thread.sleep(10000);
    }
  }

  public static void testNewFetcher() throws DecoderException, UnknownHostException {
    MetadataFetcher fetcher = new MetadataFetcher();
    List<String> seeds =
        new ArrayList() {
          {
            //            add("e27f683e1cf18ba789e4db29552550cd537a2dab 54.190.185.139:8114");
            //            add("bfa15e9c015e14a0f4d2dbb1d37850395716ae7e 159.224.97.182:33729");
            //            add("df009e2eae1263c08e83e87bc5ceca11e9900a67 83.233.20.59:21643");
            //            add("7b1e58d398e808d6242d8e1cd4c91ffe02539cbb 79.115.170.168:58759");
            add("1ae4ec6fe6478f4c04a7b2782b47a0922f5ed818 93.70.247.194:28800");
          }
        };
    for (String seed : seeds) {
      String[] tmps = seed.split(" ");
      BencodedString infohash = new BencodedString(Hex.decodeHex(tmps[0].toCharArray()));
      Peer peer =
          new Peer(
              InetAddress.getByName(tmps[1].split(":")[0]),
              Integer.parseInt(tmps[1].split(":")[1]));
      for (int i = 0; i < 3; i++) {
        fetcher.submit(
            infohash,
            peer,
            new MetadataListener() {
              @Override
              public void onSuccedded(
                  Peer peer, BencodedString infohash, byte[] metadata, long costtime) {
                System.out.println("fetched");
                Bencoding e = new Bencoding(metadata);
                try {
                  System.out.println(e.decode().toHuman());
                } catch (InvalidBittorrentPacketException e1) {
                  e1.printStackTrace();
                }
              }

              @Override
              public void onFailed(Peer peer, BencodedString infohash, Throwable t, long costtime) {
                t.printStackTrace();
              }
            });
      }
    }
  }

  public static void testDHT() throws SocketException, InterruptedException {
    MetricRegistry registry = new MetricRegistry();
    DHT dht =
        new DHT(
            new BittorrentConfig(),
            new MetaWatcher() {
              @Override
              public void onGetInfoHash(BencodedString infohash) {
                System.out.println("get infohash, " + infohash.asHexString());
              }

              @Override
              public void onAnnouncePeer(BencodedString infohash, Peer peer) {
                System.out.println("announce peer infohash, " + infohash.asHexString());
              }
            },
            registry);
  }

  public static void testFetcher() {
    try {
      System.out.println("Hello World!");
      ConcurrentSkipListSet<MetaFetcher> fetchers = new ConcurrentSkipListSet<>();
      BencodedString infohash =
          new BencodedString(
              Hex.decodeHex("1465bb33acf81c2725eb62fb14a4e206d5af6a69".toCharArray()));
      Peer peer = new Peer(InetAddress.getByName("221.143.172.251"), 40937);
      MetaFetcher fetcher1 =
          new MetaFetcher(
              infohash,
              peer,
              new MetaFetchWatcher() {
                @Override
                public void onSuccessed(
                    BencodedString infohash, Peer peer, byte[] metadata, long costtime) {}

                @Override
                public void onException(
                    BencodedString infohash, Peer peer, Throwable t, long costtime) {}
              });
      System.out.println(fetchers.add(fetcher1));
      System.out.println(fetchers.add(fetcher1));
      System.out.println(fetchers.add(fetcher1));
      System.out.println(fetchers.size());
      System.out.println(fetchers.remove(fetcher1));
      System.out.println(fetchers.size());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
