package com.killxdcj.aiyawocao;

import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.bencoding.IBencodedValue;
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

    public static void testNormalFetcher() {
        try {
            MetadataFetcher fetcher = new MetadataFetcher(InetAddress.getByName("35.159.11.252"), 8114, new BencodedString(Hex.decodeHex("d779ef6d7f805488759bc78068ad1be9aa026755".toCharArray())), new MetadataFetcher.IFetcherCallback() {
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

    public static void testNIOFetcher() {
        try {
            BencodedString infohash = new BencodedString(Hex.decodeHex("d779ef6d7f805488759bc78068ad1be9aa026755".toCharArray()));
            NIOMetaFetcher fetcher = new NIOMetaFetcher(new MetricRegistry(),60*1000, 5);
            Peer peer = new Peer(InetAddress.getByName("35.159.11.252"), 8114);
            fetcher.submit(infohash, peer, new MetaFetchWatcher() {
                @Override
                public void onSuccessed(BencodedString infohash, Peer peer, byte[] metadata, long costtime) {
                    System.out.println("successed");
                }

                @Override
                public void onException(BencodedString infohash, Peer peer, Throwable t, long costtime) {
                    t.printStackTrace();
                }
            });
            while (true) {
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
