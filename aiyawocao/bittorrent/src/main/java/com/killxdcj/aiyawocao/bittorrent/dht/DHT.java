package com.killxdcj.aiyawocao.bittorrent.dht;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedMap;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.bencoding.IBencodedValue;
import com.killxdcj.aiyawocao.bittorrent.config.BittorrentConfig;
import com.killxdcj.aiyawocao.bittorrent.exception.InvalidBittorrentPacketException;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import com.killxdcj.aiyawocao.bittorrent.utils.JTorrentUtils;
import com.revinate.guava.util.concurrent.RateLimiter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DHT {

  public static final Logger LOGGER = LoggerFactory.getLogger(DHT.class);

  private BittorrentConfig config;
  private MetaWatcher metaWatcher;
  private MetricRegistry metricRegistry;
  private BencodedString nodeId;
  private DatagramSocket datagramSocket;
  private TransactionManager transactionManager;
  private NodeManager nodeManager;
  private volatile boolean exit = false;
  private Thread workProcThread;
  private Thread nodefindThread;
  private RateLimiter requestLimiter;
  private RateLimiter outBandwidthLimit;
  private BlackListManager blkManager;
  private boolean enableBlk;
  private int nodeidChangeThreshold;
  private RateLimiter findNodeLimiter;

  private Meter inBoundwidthMeter;
  private Meter outBoundwidthMeter;
  private Meter queryMeter;
  private Meter responseMeter;
  private Meter errorMeter;
  private Meter findNodeMeter;
  private Meter discardNodeMeter;
  private Meter getpeersMeter;
  private Meter announcePeerMeter;
  private Counter neighborEmpty;
  private Meter pingNodeReqMeter;
  private Meter pingNodeRespMeter;

  public DHT(BittorrentConfig config, MetaWatcher metaWatcher, MetricRegistry metricRegistry)
      throws SocketException {
    this.config = config;
    this.metaWatcher = metaWatcher;
    this.metricRegistry = metricRegistry;
    this.nodeidChangeThreshold = config.getNodeidChangeThreshold();
    enableBlk = config.getEnableBlack();
    initMetrics();
    nodeId = JTorrentUtils.genNodeId();
    if (config.getRequestLimit() != -1) {
      requestLimiter = RateLimiter.create(config.getRequestLimit());
    }
    if (config.getOutBandwidthLimit() != -1) {
      outBandwidthLimit = RateLimiter.create(config.getOutBandwidthLimit());
    }
    if (config.getFindNodeLimit() != -1) {
      findNodeLimiter = RateLimiter.create(config.getFindNodeLimit());
    }
    int port = config.getPort();
    if (port == -1) {
      port = 10000 + (new Random(System.currentTimeMillis())).nextInt(10000);
    }
    datagramSocket = new DatagramSocket(port);
    nodeManager = new NodeManager(config.getMaxNeighbor());
    blkManager = new BlackListManager(config.getBlackThreshold());
    transactionManager = new TransactionManager();
    workProcThread = new Thread(this::workProc);
    workProcThread.start();
    nodefindThread = new Thread(this::nodeFindProc);
    nodefindThread.start();
    LOGGER.info("DHT start, nodeId:{}, port:{}", nodeId.asHexString(), port);
  }

  private void initMetrics() {
    inBoundwidthMeter = metricRegistry.meter(MetricRegistry.name(DHT.class, "DHTInBoundwidth"));
    outBoundwidthMeter = metricRegistry.meter(MetricRegistry.name(DHT.class, "DHTOutBoundwidth"));
    queryMeter = metricRegistry.meter(MetricRegistry.name(DHT.class, "DHTQuery"));
    responseMeter = metricRegistry.meter(MetricRegistry.name(DHT.class, "DHTResponse"));
    errorMeter = metricRegistry.meter(MetricRegistry.name(DHT.class, "DHTError"));
    findNodeMeter = metricRegistry.meter(MetricRegistry.name(DHT.class, "DHTRequestFindNode"));
    discardNodeMeter = metricRegistry.meter(MetricRegistry.name(DHT.class, "DHTDiscardNode"));
    getpeersMeter = metricRegistry.meter(MetricRegistry.name(DHT.class, "DHTQueryGetPeers"));
    announcePeerMeter =
        metricRegistry.meter(MetricRegistry.name(DHT.class, "DHTQueryAnnouncePeer"));
    neighborEmpty = metricRegistry.counter(MetricRegistry.name(DHT.class, "DHTNeighborEmpty"));
    pingNodeReqMeter = metricRegistry.meter(MetricRegistry.name(DHT.class, "DHTPingNodeReq"));
    pingNodeRespMeter = metricRegistry.meter(MetricRegistry.name(DHT.class, "DHTPingNodeResp"));
  }

  public void shutdown() {
    exit = true;
    blkManager.shutdown();
    nodefindThread.interrupt();
    workProcThread.interrupt();
    transactionManager.shutdown();
    datagramSocket.close();
  }

  private void workProc() {
    Thread.currentThread().setName("DHT Main WorkProc");
    int maxPacketSize = config.getMaxPacketSize();
    KRPC krpc;
    while (!exit) {
      try {
        DatagramPacket packet = new DatagramPacket(new byte[maxPacketSize], maxPacketSize);
        datagramSocket.receive(packet);
        inBoundwidthMeter.mark(packet.getLength());
        IBencodedValue value = new Bencoding(packet.getData(), 0, packet.getLength()).decode();
        krpc = new KRPC((BencodedMap) value);
        krpc.validate();
        switch (krpc.transType()) {
          case QUERY:
            queryMeter.mark();
            handleQuery(packet, krpc);
            break;
          case RESPONSE:
            responseMeter.mark();
            handleResponse(packet, krpc);
            break;
          case ERR:
            errorMeter.mark();
            handleResponseError(packet, krpc);
            break;
          default:
            // can't run here
            break;
        }
      } catch (IOException e) {
        LOGGER.error("DHT Main WorkProc error", e);
      } catch (InvalidBittorrentPacketException e) {
        LOGGER.debug("decode bittorrent packet error", e);
      } catch (Throwable t) {
        LOGGER.error("DHT Main WorkProc fetal error", t);
      }
    }
    LOGGER.info("DHT Main WorkProc exit");
  }

  private void nodeFindProc() {
    Thread.currentThread().setName("DHT FindNode Proc");
    int idx = 0;
    BencodedString neighborId = null;
    while (!exit) {
      if (findNodeLimiter != null) {
        findNodeLimiter.acquire();
      }

      try {
        if (idx == 0 || neighborId == null) {
          byte[] randomId = Arrays.copyOf(nodeId.asBytes(), 20);
          byte[] randomIdNext = JTorrentUtils.genByte(10);
          for (int i = 0; i < randomIdNext.length; i++) {
            randomId[i] = randomIdNext[i];
          }
          neighborId = new BencodedString(randomId);
        }

        Node node = nodeManager.getNode();
        if (node == null) {
          neighborEmpty.inc();
          for (String primeNode : config.getPrimeNodes()) {
            String[] ipPort = primeNode.split(":");
            node = new Node(InetAddress.getByName(ipPort[0]), Integer.parseInt(ipPort[1]));
            sendFindNodeReq(node, neighborId);
          }
        } else {
          sendFindNodeReq(node, buildDummyNodeId(node.id), neighborId);
          findNodeMeter.mark();
        }
        idx = ++idx % this.nodeidChangeThreshold;
      } catch (Throwable e) {
        LOGGER.error("nodeFindProc error", e);
      }
    }
  }

  private int sendFindNodeReq(
      Node node, BencodedString dummyLocalNodeId, BencodedString targetNodeId) {
    try {
      KRPC krpc = KRPC.buildFindNodeReqPacket(dummyLocalNodeId, targetNodeId);
      transactionManager.putTransaction(
          new Transaction(node, krpc, config.getTransactionExpireTime()));
      return sendKrpcPacket(node, krpc);
    } catch (Exception e) {
      LOGGER.error("sendFindNodeReq error, node:{}", node, e);
      return 0;
    }
  }

  private int sendFindNodeReq(Node node, BencodedString targetNodeId) {
    return sendFindNodeReq(node, this.nodeId, targetNodeId);
  }

  private int sendPingNodeReq(Node node, BencodedString localNodeId) {
    try {
      KRPC krpc = KRPC.buildPingReqPacket(localNodeId);
      transactionManager.putTransaction(new Transaction(node, krpc, config.getTransactionExpireTime()));
      return sendKrpcPacket(node, krpc);
    } catch (Exception e) {
      LOGGER.error("sendPingNodeReq error, node:{}", node, e);
      return 0;
    }
  }

  private int sendKrpcPacket(Node node, KRPC krpc) throws IOException {
    //		transactionManager.putTransaction(new Transaction(node, krpc,
    // config.getTransactionExpireTime()));
    if (requestLimiter != null) {
      requestLimiter.acquire();
    }
    byte[] packetBytes = krpc.encode();
    DatagramPacket udpPacket =
        new DatagramPacket(packetBytes, 0, packetBytes.length, node.getAddr(), node.getPort());
    datagramSocket.send(udpPacket);
    outBoundwidthMeter.mark(packetBytes.length);
    if (outBandwidthLimit != null) {
      outBandwidthLimit.acquire(packetBytes.length);
    }
    return packetBytes.length;
  }

  private void handleQuery(DatagramPacket packet, KRPC krpc) throws IOException {
    LOGGER.debug(
        "recv request packet, id:{}, action:{}, ip:{}, port:{}",
        krpc.getId(),
        krpc.action(),
        packet.getAddress().getHostAddress(),
        packet.getPort());
    switch (krpc.action()) {
      case PING:
        // ignore
        break;
      case FIND_NODE:
        // ingore
        break;
      case GET_PEERS:
        handleGetPeersQuery(packet, krpc);
        break;
      case ANNOUNCE_PEER:
        handleAnnouncePeerQuery(packet, krpc);
        break;
      default:
        LOGGER.warn("unsupport krpc packet action, packet:{}", krpc);
    }
  }

  private void handleGetPeersQuery(DatagramPacket packet, KRPC krpc) throws IOException {
    String addr = "" + packet.getAddress().getHostAddress() + ":" + packet.getPort();
    if (enableBlk && blkManager.isInBlackList(addr)) {
      return;
    }

    blkManager.mark(addr);
    getpeersMeter.mark();
    Node node = new Node(krpc.getId(), packet.getAddress(), packet.getPort());
    BencodedMap reqArgs = (BencodedMap) krpc.getData().get(KRPC.QUERY_ARGS);
    BencodedString infohash = (BencodedString) reqArgs.get(KRPC.INFO_HASH);
    metaWatcher.onGetInfoHash(infohash);

    List<Node> nodes = nodeManager.getPeers();
    List<Peer> peers = new ArrayList<>();
    for (Node nodex : nodes) {
      peers.add(new Peer(nodex.getAddr(), node.getPort()));
    }
    if (peers.size() == 0) {
      LOGGER.warn("Peers is empty, maybe neighbors is empty");
    }

    KRPC resp =
        KRPC.buildGetPeersRespPacketWithPeers(
            krpc.getTransId(), buildDummyNodeId(infohash), "caojian", peers);
    sendKrpcPacket(node, resp);
  }

  private void handleAnnouncePeerQuery(DatagramPacket packet, KRPC krpc) throws IOException {
    announcePeerMeter.mark();
    BencodedMap reqData = (BencodedMap) krpc.getData().get(KRPC.QUERY_ARGS);
    BencodedString infohash = (BencodedString) reqData.get(KRPC.INFO_HASH);

    int port = reqData.get(KRPC.PORT).asLong().intValue();
    if (reqData.containsKey(KRPC.IMPLIED_PORT) && reqData.get(KRPC.IMPLIED_PORT).asLong() != 0) {
      port = packet.getPort();
    }

    Peer peer = new Peer(packet.getAddress(), port);
    metaWatcher.onAnnouncePeer(infohash, peer);

    KRPC resp = KRPC.buildAnnouncePeerRespPacket(krpc.getTransId(), buildDummyNodeId(krpc.getId()));
    Node node = new Node(krpc.getId(), packet.getAddress(), packet.getPort());
    sendKrpcPacket(node, resp);
  }

  private void handleResponse(DatagramPacket packet, KRPC krpc) {
    Transaction transaction = transactionManager.getTransaction(krpc.getTransId());
    if (transaction == null) {
      LOGGER.debug(
          "transaction not exist, node:{}:{}, krpc:{}",
          packet.getAddress(),
          packet.getPort(),
          krpc);
      return;
    }

    switch (transaction.getKrpc().action()) {
      case PING:
        // ignore
        pingNodeRespMeter.mark();
        break;
      case FIND_NODE:
        handleFindNodeResponse(krpc);
        break;
      case GET_PEERS:
        break;
      case ANNOUNCE_PEER:
        break;
      default:
        LOGGER.warn(
            "unsupport krpc packet action, action:{}, packet:{}",
            transaction.getKrpc().action(),
            krpc);
    }
  }

  private void handleFindNodeResponse(KRPC resp) {
    BencodedMap respData = (BencodedMap) resp.getData().get(KRPC.RESPONSE_DATA);
    if (!respData.containsKey(KRPC.NODES)) {
      return;
    }

    List<Node> nodes = JTorrentUtils.deCompactNodeInfos(respData.get(KRPC.NODES).asBytes());
    for (Node node : nodes) {
      if (node.port != 0) {
        if (!nodeManager.putNode(node)) {
          if (config.getEnablePing()) {
            sendPingNodeReq(node, buildDummyNodeId(node.id));
            pingNodeReqMeter.mark();
          } else {
            discardNodeMeter.mark();
          }
        }
      }
    }
  }

  private void handleResponseError(DatagramPacket packet, KRPC krpc) {
    LOGGER.debug(
        "recive error resp, from: {}:{}, packet:{}", packet.getAddress(), packet.getPort(), krpc);
  }

  private BencodedString buildDummyNodeId(BencodedString targetId) {
    byte[] dummyId = new byte[20];
    System.arraycopy(targetId.asBytes(), 0, dummyId, 0, 15);
    System.arraycopy(nodeId.asBytes(), 15, dummyId, 15, 5);
    return new BencodedString(dummyId);
  }
}
