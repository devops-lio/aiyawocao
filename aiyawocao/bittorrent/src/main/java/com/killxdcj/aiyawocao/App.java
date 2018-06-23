package com.killxdcj.aiyawocao;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ConcurrentHashMultiset;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.peer.MetaFetchWatcher;
import com.killxdcj.aiyawocao.bittorrent.peer.MetaFetcher;
import com.killxdcj.aiyawocao.bittorrent.peer.NIOMetaFetcher;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import org.apache.commons.codec.binary.Hex;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        try {
            System.out.println("Hello World!");
            ConcurrentSkipListSet<MetaFetcher> fetchers = new ConcurrentSkipListSet<>();
            BencodedString infohash = new BencodedString(Hex.decodeHex("1465bb33acf81c2725eb62fb14a4e206d5af6a69".toCharArray()));
            Peer peer = new Peer(InetAddress.getByName("221.143.172.251"), 40937);
            MetaFetcher fetcher1 = new MetaFetcher(infohash, peer, new MetaFetchWatcher() {
                @Override
                public void onSuccessed(BencodedString infohash, Peer peer, byte[] metadata, long costtime) {

                }

                @Override
                public void onException(BencodedString infohash, Peer peer, Throwable t, long costtime) {

                }
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
