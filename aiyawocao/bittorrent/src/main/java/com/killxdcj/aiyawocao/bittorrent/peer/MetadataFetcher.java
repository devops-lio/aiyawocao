package com.killxdcj.aiyawocao.bittorrent.peer;

import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedInteger;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedMap;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.utils.JTorrentUtils;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataFetcher extends Peer implements Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetadataFetcher.class);

  private static final int DEFAULT_FETCH_TIMEOUT = 5 * 60 * 1000;
  private static final int DEFAULT_CONNECT_CHECK_PERIOD = 500;
  private static final byte EXTENDED = (byte) 20;
  private static final byte HANDSHAKE = (byte) 0;
  private static final byte UT_METADATA = (byte) 1;
  private static final int BLOCK_SIZE = 16 * 1024;
  private static final int REQUEST = 0;
  private static final int DATA = 1;
  private static final int REJECT = 2;
  private static final byte[] handshakePrefix = buildHandshakePacketPrefix();
  private BencodedString infohash;
  private IFetcherCallback iFetcherCallback;
  private SocketChannel cliChannel;
  private BencodedString peerId;
  private volatile boolean finshed = false;
  private byte remoteUtMetadataId;
  private int meatadataSize;
  private int pieceTotal;
  private Map<Integer, byte[]> metadata = new HashMap<>();
  private volatile boolean exit = false;

  public MetadataFetcher(Peer peer, BencodedString infohash, IFetcherCallback iFetcherCallback) {
    super(peer.addr, peer.port);
    this.infohash = infohash;
    this.iFetcherCallback = iFetcherCallback;
    peerId = JTorrentUtils.genNodeId();
  }

  public MetadataFetcher(
      InetAddress addr, int port, BencodedString infohash, IFetcherCallback iFetcherCallback) {
    super(addr, port);
    this.infohash = infohash;
    this.iFetcherCallback = iFetcherCallback;
    peerId = JTorrentUtils.genPeerId();
  }

  private static byte[] buildHandshakePacketPrefix() {
    ByteBuffer packet = ByteBuffer.allocate(28); // 48 = 1 + 19 + 8 + 20
    packet.put((byte) 19);
    packet.put("BitTorrent protocol".getBytes());
    packet.put(
        new byte[]{
            (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 16, (byte) 0, (byte) 1
        });
    return packet.array();
  }

  @Override
  public void run() {
    Thread.currentThread()
        .setName(
            "MetaFetcher " + infohash.asHexString() + ", " + addr.getHostAddress() + ":" + port);
    try {
      cliChannel = SocketChannel.open();
      cliChannel.configureBlocking(false);
      if (!cliChannel.connect(new InetSocketAddress(addr, port))) {
        while (!cliChannel.finishConnect()) {
          Thread.sleep(DEFAULT_CONNECT_CHECK_PERIOD);
        }
      }

      sendHandShake();

      ByteBuffer handshakeResp = readPacket(68);
      byte[] respBytes = handshakeResp.array();
      if (!Arrays.equals(Arrays.copyOf(handshakePrefix, 20), Arrays.copyOf(respBytes, 20))
          || (handshakeResp.array()[25] & 0x10) == 0) {
        throw new Exception("handshake error, prefix data error");
      }

      if (!infohash.equals(new BencodedString(Arrays.copyOfRange(respBytes, 28, 48)))) {
        throw new Exception("handshake error, infohash is diferent");
      }
      LOGGER.info(
          "connected to peer:{}",
          new BencodedString(Arrays.copyOfRange(respBytes, 48, 68)).asHexString());

      sendExtHandshake();

      while (!finshed) {
        ByteBuffer transPacket = readPacket();
        dealPacket(transPacket);
      }
    } catch (Exception e) {
      if (!exit) {
        exit = true;
        iFetcherCallback.onException(e);
        return;
      }
    } finally {
      closeCliChannel();
      exit = true;
      Thread.currentThread().setName("MetaFetcher ThreadPool");
    }
  }

  private void dealPacket(ByteBuffer packet) throws Exception {
    if (packet.array().length == 0) {
      // Messages of length zero are keepalives, and ignored
      return;
    }

    byte extended = packet.get();
    if (extended != EXTENDED) {
      LOGGER.warn("get unknow packet, extended:{}", extended);
      return;
    }

    byte msgType = packet.get();
    switch (msgType) {
      case HANDSHAKE:
        onHandShake(packet);
        break;
      case UT_METADATA:
        onUtMetadata(packet);
        break;
      default:
        LOGGER.warn("unknow packet, msgtype:{}", msgType);
    }
  }

  private void onHandShake(ByteBuffer packet) throws Exception {
    Bencoding bencoding =
        new Bencoding(Arrays.copyOfRange(packet.array(), 2, packet.array().length));
    BencodedMap bencodedMap = (BencodedMap) bencoding.decode();
    if (!bencodedMap.containsKey("m") || !bencodedMap.containsKey("metadata_size")) {
      throw new Exception(
          "invalid ExtHandshake packet, m or metadata_size is missed, packet:"
              + bencodedMap.toString());
    }

    remoteUtMetadataId =
        ((BencodedMap) bencodedMap.get("m")).get("ut_metadata").asLong().byteValue();
    meatadataSize = bencodedMap.get("metadata_size").asLong().intValue();
    if (meatadataSize % BLOCK_SIZE > 0) {
      pieceTotal = meatadataSize / BLOCK_SIZE + 1;
    }

    for (int i = 0; i < pieceTotal; i++) {
      BencodedMap req = new BencodedMap();
      req.put("msg_type", new BencodedInteger(REQUEST));
      req.put("piece", new BencodedInteger(i));
      sendPacket(buildPacket(remoteUtMetadataId, req.serialize()));
    }
  }

  private void onUtMetadata(ByteBuffer packet) throws Exception {
    byte[] packetBytes = packet.array();
    Bencoding bencoding = new Bencoding(Arrays.copyOfRange(packetBytes, 2, packetBytes.length));
    BencodedMap bencodedMap = (BencodedMap) bencoding.decode();
    if (!bencodedMap.containsKey("msg_type")) {
      throw new Exception("invalid ExtHandshake packet, msg_type is missed");
    }

    int msgType = bencodedMap.get("msg_type").asLong().intValue();
    switch (msgType) {
      case REQUEST:
        // can't come to here
        LOGGER.warn("metadataFetcher get request...");
        break;
      case DATA:
        int piece = bencodedMap.get("piece").asLong().intValue();
        byte[] data =
            Arrays.copyOfRange(packetBytes, 2 + bencoding.getCurIndex(), packetBytes.length);
        if (piece > pieceTotal - 1) {
          throw new Exception("piece outof range");
        }

        if (pieceTotal == 1) {
          if (meatadataSize != data.length) {
            throw new Exception("piece size not match");
          }
        } else {
          if (piece != pieceTotal - 1) {
            if (data.length != BLOCK_SIZE) {
              throw new Exception("piece is not the last and size need to be 16KB");
            }
          } else {
            if (data.length != meatadataSize % BLOCK_SIZE) {
              throw new Exception("piece is the last one and size is error");
            }
          }
        }

        metadata.put(piece, data);
        LOGGER.info(
            "fetched metadata piece, infohash:{}, total:{}, cur:{}, size:{}bytes",
            infohash.asHexString(),
            pieceTotal,
            piece,
            data.length);

        if (metadata.size() == pieceTotal) {
          ByteBuffer buf = ByteBuffer.allocate(meatadataSize);
          for (int i = 0; i < pieceTotal; i++) {
            buf.put(metadata.get(i));
          }

          if (!infohash.asHexString().equals(DigestUtils.sha1Hex(buf.array()))) {
            throw new Exception("fetched metadata, but sha1 is error");
          } else {
            LOGGER.info(
                "fethched metadata, infohash:{}, ip:{}, port:{}",
                infohash.asHexString(),
                addr.getHostAddress(),
                port);
            iFetcherCallback.onFinshed(infohash, buf.array());
            finshed = true;
          }
        }
        break;
      case REJECT:
        throw new Exception(
            "peer reject, it doesn't have piece:" + bencodedMap.get("piece").asLong().intValue());
    }
  }

  private ByteBuffer buildPacket(byte extendedId, byte[] payload) {
    int length = 4 + 2 + payload.length;
    ByteBuffer ret = ByteBuffer.allocate(length);
    ret.putInt(2 + payload.length);
    ret.put(EXTENDED);
    ret.put(extendedId);
    ret.put(payload);
    ret.flip();
    return ret;
  }

  private void sendHandShake() throws IOException {
    sendPacket(ByteBuffer.wrap(handshakePrefix));
    sendPacket(ByteBuffer.wrap(infohash.asBytes()));
    sendPacket(ByteBuffer.wrap(peerId.asBytes()));
  }

  private void sendExtHandshake() throws IOException {
    BencodedMap extHandshake = new BencodedMap();
    BencodedMap data = new BencodedMap();
    data.put("ut_metadata", new BencodedInteger(UT_METADATA));
    extHandshake.put("m", data);
    sendPacket(buildPacket(HANDSHAKE, extHandshake.serialize()));
  }

  private void sendPacket(ByteBuffer packet) throws IOException {
    while (packet.hasRemaining()) {
      cliChannel.write(packet);
    }
  }

  private ByteBuffer readPacket(int length) throws IOException {
    ByteBuffer byteBuffer = ByteBuffer.allocate(length);
    while (byteBuffer.hasRemaining()) {
      if (cliChannel.read(byteBuffer) <= -1) {
        throw new IOException("try read " + length + " bytes, but server close connect");
      }
    }

    byteBuffer.flip();
    return byteBuffer;
  }

  private ByteBuffer readPacket() throws IOException {
    ByteBuffer lengthBuf = readPacket(4);
    ByteBuffer ret = readPacket(lengthBuf.getInt());
    return ret;
  }

  private void closeCliChannel() {
    if (cliChannel != null && cliChannel.isConnected()) {
      try {
        cliChannel.close();
      } catch (IOException e) {
        //                LOGGER.error("close clichannel error", e);
      }
    }
  }

  public interface IFetcherCallback {

    void onFinshed(BencodedString infohash, byte[] metadata);

    void onException(Exception e);
  }
}
