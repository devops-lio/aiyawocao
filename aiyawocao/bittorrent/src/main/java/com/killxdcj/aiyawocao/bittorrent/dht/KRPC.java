package com.killxdcj.aiyawocao.bittorrent.dht;

import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedInteger;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedList;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedMap;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.exception.InvalidBittorrentPacketException;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import com.killxdcj.aiyawocao.bittorrent.utils.JTorrentUtils;

import java.nio.ByteBuffer;
import java.util.List;

public class KRPC {
  public static final String TRANS_ID = "t";
  public static final String TRANS_TYPE = "y";
  public static final String QUERY_ACTION = "q";
  public static final String QUERY_ARGS = "a";
  public static final String RESPONSE_DATA = "r";
  public static final String ID = "id";
  public static final String TARGET = "target";
  public static final String INFO_HASH = "info_hash";
  public static final String IMPLIED_PORT = "implied_port";
  public static final String PORT = "port";
  public static final String TOKEN = "token";
  public static final String NODES = "nodes";
  public static final String VALUES = "values";
  public static final String ERR_DATA = "e";
  private BencodedMap data;

  public KRPC(BencodedMap data) {
    this.data = data;
  }

  public static KRPC buildPingReqPacket(BencodedString localNodeId) {
    BencodedString transId = TransactionManager.genTransactionId();
    BencodedMap packet = new BencodedMap();
    packet.put(TRANS_ID, transId);
    packet.put(TRANS_TYPE, new BencodedString(TransType.QUERY.getValue()));
    packet.put(QUERY_ACTION, new BencodedString(Action.PING.getValue()));
    BencodedMap param = new BencodedMap();
    param.put(ID, localNodeId);
    packet.put(QUERY_ARGS, param);
    return new KRPC(packet);
  }

  public static KRPC buildPingRespPacket(BencodedString transId, BencodedString localNodeId) {
    BencodedMap packet = new BencodedMap();
    packet.put(TRANS_ID, transId);
    packet.put(TRANS_TYPE, new BencodedString(TransType.RESPONSE.getValue()));
    BencodedMap param = new BencodedMap();
    param.put(ID, localNodeId);
    packet.put(RESPONSE_DATA, param);
    return new KRPC(packet);
  }

  public static KRPC buildFindNodeReqPacket(
      BencodedString localNodeId, BencodedString targetNodeId) {
    BencodedString transId = TransactionManager.genTransactionId();
    BencodedMap packet = new BencodedMap();
    packet.put(TRANS_ID, transId);
    packet.put(TRANS_TYPE, new BencodedString(TransType.QUERY.getValue()));
    packet.put(QUERY_ACTION, new BencodedString(Action.FIND_NODE.getValue()));
    BencodedMap param = new BencodedMap();
    param.put(ID, localNodeId);
    param.put(TARGET, targetNodeId);
    packet.put(QUERY_ARGS, param);
    return new KRPC(packet);
  }

  public static KRPC buildFindNodeRespPacket(
      BencodedString transId, BencodedString localNodeId, List<Node> nodes) {
    BencodedMap packet = new BencodedMap();
    packet.put(TRANS_ID, transId);
    packet.put(TRANS_TYPE, new BencodedString(TransType.RESPONSE.getValue()));
    BencodedMap param = new BencodedMap();
    param.put(ID, localNodeId);
    ByteBuffer byteBuffer = ByteBuffer.allocate(26 * nodes.size());
    for (Node node : nodes) {
      byteBuffer.put(JTorrentUtils.compactNodeInfo(node));
    }
    param.put(NODES, new BencodedString(byteBuffer.array()));
    packet.put(RESPONSE_DATA, param);
    return new KRPC(packet);
  }

  public static KRPC buildGetPeersReqPacket(BencodedString localNodeId, BencodedString infoHash) {
    BencodedString transId = TransactionManager.genTransactionId();
    BencodedMap packet = new BencodedMap();
    packet.put(TRANS_ID, transId);
    packet.put(TRANS_TYPE, new BencodedString(TransType.QUERY.getValue()));
    packet.put(QUERY_ACTION, new BencodedString(Action.GET_PEERS.getValue()));
    BencodedMap param = new BencodedMap();
    param.put(ID, localNodeId);
    param.put(INFO_HASH, infoHash);
    packet.put(QUERY_ARGS, param);
    return new KRPC(packet);
  }

  public static KRPC buildGetPeersRespPacketWithPeers(
      BencodedString transId, BencodedString localNodeId, String token, List<Peer> peers) {
    BencodedMap packet = new BencodedMap();
    packet.put(TRANS_ID, transId);
    packet.put(TRANS_TYPE, new BencodedString(TransType.RESPONSE.getValue()));
    BencodedMap param = new BencodedMap();
    param.put(ID, localNodeId);
    param.put(TOKEN, new BencodedString(token));
    BencodedList bencodedPeers = new BencodedList();
    for (Peer peer : peers) {
      bencodedPeers.add(new BencodedString(JTorrentUtils.compactPeerInfo(peer)));
    }
    param.put(VALUES, bencodedPeers);
    packet.put(RESPONSE_DATA, param);
    return new KRPC(packet);
  }

  public static KRPC buildGetPeersRespPacketWithNodes(
      BencodedString transId, BencodedString localNodeId, String token, List<Node> nodes) {
    BencodedMap packet = new BencodedMap();
    packet.put(TRANS_ID, transId);
    packet.put(TRANS_TYPE, new BencodedString(TransType.RESPONSE.getValue()));
    BencodedMap param = new BencodedMap();
    param.put(ID, localNodeId);
    param.put(TOKEN, new BencodedString(token));
    ByteBuffer byteBuffer = ByteBuffer.allocate(26 * nodes.size());
    for (Node node : nodes) {
      byteBuffer.put(JTorrentUtils.compactNodeInfo(node));
    }
    param.put(NODES, new BencodedString(byteBuffer.array()));
    packet.put(RESPONSE_DATA, param);
    return new KRPC(packet);
  }

  public static KRPC buildAnnouncePeerReqPacket(
      BencodedString localNodeId, BencodedString infoHash, String token, int port) {
    BencodedString transId = TransactionManager.genTransactionId();
    BencodedMap packet = new BencodedMap();
    packet.put(TRANS_ID, transId);
    packet.put(TRANS_TYPE, new BencodedString(TransType.QUERY.getValue()));
    packet.put(QUERY_ACTION, new BencodedString(Action.ANNOUNCE_PEER.getValue()));
    BencodedMap param = new BencodedMap();
    param.put(ID, localNodeId);
    param.put(INFO_HASH, infoHash);
    param.put(IMPLIED_PORT, new BencodedInteger(0));
    param.put(PORT, new BencodedInteger(port));
    param.put(TOKEN, new BencodedString(token));
    packet.put(QUERY_ARGS, param);
    return new KRPC(packet);
  }

  public static KRPC buildAnnouncePeerRespPacket(
      BencodedString transId, BencodedString localNodeId) {
    return buildPingRespPacket(transId, localNodeId);
  }

  public static KRPC buildErrorRespPacket(int errno, String errmsg) {
    BencodedString transId = TransactionManager.genTransactionId();
    BencodedMap packet = new BencodedMap();
    packet.put(TRANS_ID, transId);
    packet.put(TRANS_TYPE, new BencodedString(TransType.ERR.getValue()));
    BencodedList errData = new BencodedList();
    errData.add(new BencodedInteger(errno));
    errData.add(new BencodedString(errmsg));
    packet.put(ERR_DATA, errData);
    return new KRPC(packet);
  }

  public byte[] encode() {
    return this.data.serialize();
  }

  public TransType transType() {
    return TransType.fromString(data.get(TRANS_TYPE).asString());
  }

  public Action action() {
    return Action.fromString(data.get(QUERY_ACTION).asString());
  }

  public BencodedString getTransId() {
    return (BencodedString) data.get(TRANS_ID);
  }

  public BencodedMap getData() {
    return data;
  }

  public BencodedString getId() {
    if (data.containsKey(RESPONSE_DATA)) {
      return (BencodedString) ((BencodedMap) data.get(RESPONSE_DATA)).get(ID);
    } else if (data.containsKey(QUERY_ARGS)) {
      return (BencodedString) ((BencodedMap) data.get(QUERY_ARGS)).get(ID);
    }
    return null;
  }

  public BencodedString getTargetId() {
    if (data.containsKey(QUERY_ARGS)) {
      return (BencodedString) ((BencodedMap) data.get(QUERY_ARGS)).get(TARGET);
    }
    return null;
  }

  public void validate() throws InvalidBittorrentPacketException {
    if (!(data instanceof BencodedMap)) {
      throw new InvalidBittorrentPacketException("krpc packet must be dict");
    }

    BencodedMap mapData = (BencodedMap) data;
    if (!mapData.containsKey(TRANS_ID) || !mapData.containsKey(TRANS_TYPE)) {
      throw new InvalidBittorrentPacketException("invalid krpc packet, t and y key is needed");
    }

    if (mapData.get(TRANS_TYPE).asString().equals(TransType.QUERY.getValue())) {
      if (!mapData.containsKey(QUERY_ARGS) || !mapData.containsKey(QUERY_ACTION)) {
        throw new InvalidBittorrentPacketException(
            "invalid krpc packet, in query packet, a and q key is needed");
      }
    } else {
      if (!mapData.containsKey(RESPONSE_DATA)) {
        throw new InvalidBittorrentPacketException(
            "invalid krpc packet, in resp packet, r key is needed");
      }
    }

    if (transType() != TransType.QUERY
        && transType() != TransType.RESPONSE
        && transType() != TransType.ERR) {
      throw new InvalidBittorrentPacketException("unsupport packet type : " + transType());
    }
  }

  @Override
  public String toString() {
    return "KRPC{" + "data=" + data + '}';
  }

  public enum TransType {
    QUERY("q"),
    RESPONSE("r"),
    ERR("e");

    private String value;

    TransType(String value) {
      this.value = value;
    }

    public static TransType fromString(String type) {
      if (type.equals(QUERY.getValue())) {
        return QUERY;
      } else if (type.equals(RESPONSE.getValue())) {
        return RESPONSE;
      } else if (type.equals(ERR.getValue())) {
        return ERR;
      } else {
        return null;
      }
    }

    public String getValue() {
      return value;
    }
  }

  public enum Action {
    PING("ping"),
    FIND_NODE("find_node"),
    GET_PEERS("get_peers"),
    ANNOUNCE_PEER("announce_peer"),
    UNKNOW("unknow");

    private String value;

    Action(String value) {
      this.value = value;
    }

    public static Action fromString(String action) {
      if (action.equals(PING.getValue())) {
        return PING;
      } else if (action.equals(FIND_NODE.getValue())) {
        return FIND_NODE;
      } else if (action.equals(GET_PEERS.getValue())) {
        return GET_PEERS;
      } else if (action.equals(ANNOUNCE_PEER.getValue())) {
        return ANNOUNCE_PEER;
      } else {
        return UNKNOW;
      }
    }

    public String getValue() {
      return value;
    }
  }
}
