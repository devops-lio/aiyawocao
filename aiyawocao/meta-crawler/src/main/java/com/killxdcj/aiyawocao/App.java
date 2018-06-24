package com.killxdcj.aiyawocao;

import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.bencoding.IBencodedValue;
import com.killxdcj.aiyawocao.bittorrent.config.MetaFetchConfig;
import com.killxdcj.aiyawocao.bittorrent.peer.MetaFetchWatcher;
import com.killxdcj.aiyawocao.bittorrent.peer.MetadataFetcher;
import com.killxdcj.aiyawocao.bittorrent.peer.NIOMetaFetcher;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import org.apache.commons.codec.binary.Hex;

import java.net.InetAddress;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
//        testNormalFetcher();
        testNIOFetcher();
    }

    public static void testNIOFetcher() {
        try {
            BencodedString infohash = new BencodedString(Hex.decodeHex("cc82d513a72c6fdd7e6b45393592f03e2182e51f".toCharArray()));
            NIOMetaFetcher fetcher = new NIOMetaFetcher(new MetaFetchConfig(), new MetricRegistry());
            Peer peer = new Peer(InetAddress.getByName("109.201.133.134"), 20024);
            MetaFetchWatcher watcher = new MetaFetchWatcher() {
                @Override
                public void onSuccessed(BencodedString infohash, Peer peer, byte[] metadata, long costtime) {
                    System.out.println("successed");
                }

                @Override
                public void onException(BencodedString infohash, Peer peer, Throwable t, long costtime) {
                    t.printStackTrace();
                }
            };
            fetcher.submit(infohash, peer, watcher);
            fetcher.submit(infohash, peer, watcher);
            fetcher.submit(infohash, peer, watcher);
            fetcher.submit(infohash, peer, watcher);
            while (true) {
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void testNormalFetcher() {
        try {
            MetadataFetcher fetcher = new MetadataFetcher(InetAddress.getByName("221.143.172.251"), 40937,
                    new BencodedString(Hex.decodeHex("1465bb33acf81c2725eb62fb14a4e206d5af6a69".toCharArray())),
                    new MetadataFetcher.IFetcherCallback() {
                @Override
                public void onFinshed(BencodedString infohash, byte[] metadata) {
                    System.out.println("ok");
                    String xx = "";
                    for (byte x : metadata) {
                        xx += (char)x;
                    }
                    System.out.println(xx);
                    try {
                        IBencodedValue x = (new Bencoding(metadata)).decode();
                        System.out.println(x.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onException(Exception e) {
                    e.printStackTrace();
                }
            });
            Thread thread = new Thread(fetcher);
            thread.start();
            while (true) {
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
