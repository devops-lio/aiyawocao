package com.killxdcj.aiyawocao;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ConcurrentHashMultiset;
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
import com.killxdcj.aiyawocao.bittorrent.peer.NIOMetaFetcher;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws SocketException, InterruptedException, DecoderException, UnknownHostException {
//        testDHT();
        testNewFetcher();
        while (true) {
            Thread.sleep(10000);
        }
    }

    public static void testNewFetcher() throws DecoderException, UnknownHostException {
        MetadataFetcher fetcher = new MetadataFetcher();
        BencodedString infohash = new BencodedString(Hex.decodeHex("c570cf9cb7f649a457d4f38970471767c5bd5813".toCharArray()));
        Peer peer = new Peer(InetAddress.getByName("52.59.194.172"), 8102);
        for (int i = 0; i < 5; i++) {
            fetcher.submit(infohash, peer, new MetadataListener() {
                @Override
                public void onSuccedded(Peer peer, BencodedString infohash, byte[] metadata) {
                    System.out.println("fetched");
                    Bencoding e = new Bencoding(metadata);
                    try {
                        System.out.println(e.decode().toHuman());
                    } catch (InvalidBittorrentPacketException e1) {
                        e1.printStackTrace();
                    }
                }

                @Override
                public void onFailed(Peer peer, BencodedString infohash, Throwable t) {
                    t.printStackTrace();
                }
            });
        }
    }

    public static void testDHT() throws SocketException, InterruptedException {
        MetricRegistry registry = new MetricRegistry();
        DHT dht = new DHT(new BittorrentConfig(), new MetaWatcher() {
            @Override
            public void onGetInfoHash(BencodedString infohash) {
                System.out.println("get infohash, " + infohash.asHexString());
            }

            @Override
            public void onAnnouncePeer(BencodedString infohash, Peer peer) {
                System.out.println("announce peer infohash, " + infohash.asHexString());
            }
        }, registry);
    }

    public static void testFetcher() {
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
