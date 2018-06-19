package com.killxdcj.aiyawocao.bittorrent.dht;

import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedMap;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.bencoding.IBencodedValue;
import com.killxdcj.aiyawocao.bittorrent.config.BittorrentConfig;
import com.killxdcj.aiyawocao.bittorrent.exception.InvalidBittorrentPacketException;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import com.killxdcj.aiyawocao.bittorrent.utils.JTorrentUtils;
import com.revinate.guava.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DHT {
	public static final Logger LOGGER = LoggerFactory.getLogger(DHT.class);

	private BittorrentConfig config;
	private MetaWatcher metaWatcher;
	private BencodedString nodeId;
	private DatagramSocket datagramSocket;
	private TransactionManager transactionManager;
	private NodeManager nodeManager;
	private volatile boolean exit = false;
	private Thread workProcThread;
	private Thread nodefindThread;
	private RateLimiter findnodeLimiter;
	private RateLimiter outBandwidthLimit;

	public DHT(BittorrentConfig config, MetaWatcher metaWatcher) throws SocketException {
		this.config = config;
		this.metaWatcher = metaWatcher;
		nodeId = JTorrentUtils.genNodeId();
		if (config.getFindnodeLimit() != -1) {
			findnodeLimiter = RateLimiter.create(config.getFindnodeLimit());
		}
		if (config.getOutBandwidthLimit() != -1) {
			outBandwidthLimit = RateLimiter.create(config.getOutBandwidthLimit());
		}
		datagramSocket = new DatagramSocket(config.getPort());
		nodeManager = new NodeManager(config.getMaxNeighbor());
		transactionManager = new TransactionManager();
		workProcThread = new Thread(this::workProc);
		workProcThread.start();
		nodefindThread = new Thread(this::nodeFindProc);
		nodefindThread.start();
		LOGGER.info("DHT start, nodeId:{}", nodeId.asHexString());
	}

	public void shutdown() {
		exit = true;
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
				IBencodedValue value = new Bencoding(packet.getData(), 0, packet.getLength()).decode();
				krpc = new KRPC((BencodedMap)value);
				krpc.validate();
				switch (krpc.transType()) {
					case QUERY:
						handleQuery(packet, krpc);
						break;
					case RESPONSE:
						handleResponse(packet, krpc);
						break;
					case ERR:
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
			try {
				if (findnodeLimiter != null) {
					findnodeLimiter.acquire();
				}

				if (idx == 0 || neighborId == null) {
					byte[] randomId = Arrays.copyOf(nodeId.asBytes(), 20);
					byte[] randomIdNext = JTorrentUtils.genByte(10);
					for (int i = 0; i < randomIdNext.length; i++) {
						randomId[i] = randomIdNext[i];
					}
					neighborId = new BencodedString(randomId);
				}

				Node node = nodeManager.getNode();
				int sendedData = 0;
				if (node == null) {
					for (String primeNode : config.getPrimeNodes()) {
						String[] ipPort = primeNode.split(":");
						node = new Node(InetAddress.getByName(ipPort[0]), Integer.parseInt(ipPort[1]));
						sendedData += sendFindNodeReq(node, neighborId);
					}
				} else {
					sendedData = sendFindNodeReq(node, neighborId);
				}
				idx = ++idx % 1000;
				if (outBandwidthLimit != null) {
					outBandwidthLimit.acquire(sendedData);
				}
			} catch (Throwable e) {
				LOGGER.error("nodeFindProc error", e);
			}
		}
	}

	private int sendFindNodeReq(Node node, BencodedString targetNodeId) {
		try {
			KRPC krpc = KRPC.buildFindNodeReqPacket(this.nodeId, targetNodeId);
			transactionManager.putTransaction(new Transaction(node, krpc, config.getTransactionExpireTime()));
			return sendKrpcPacket(node, krpc);
		} catch (Exception e) {
			LOGGER.error("sendFindNodeReq error, node:{}", node, e);
			return 0;
		}
	}

	private int sendKrpcPacket(Node node, KRPC krpc) throws IOException {
//		transactionManager.putTransaction(new Transaction(node, krpc, config.getTransactionExpireTime()));
		byte[] packetBytes = krpc.encode();
		DatagramPacket udpPacket = new DatagramPacket(packetBytes, 0, packetBytes.length, node.getAddr(), node.getPort());
		datagramSocket.send(udpPacket);
		return packetBytes.length;
	}

	private void handleQuery(DatagramPacket packet, KRPC krpc) throws IOException {
		LOGGER.debug("recv request packet, id:{}, action:{}, ip:{}, port:{}",
				krpc.getId(), krpc.action(), packet.getAddress().getHostAddress(), packet.getPort());
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
		Node node = new Node(krpc.getId(), packet.getAddress(), packet.getPort());
		BencodedMap reqArgs = (BencodedMap)krpc.getData().get(KRPC.QUERY_ARGS);
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

		byte[] neighborId = new byte[20];
		for (int i = 0; i < 15; i++) {
			neighborId[i] = infohash.asBytes()[i];
		}
		for (int i = 15; i < 20; i++) {
			neighborId[i] = nodeId.asBytes()[i];
		}

		KRPC resp = KRPC.buildGetPeersRespPacketWithPeers(krpc.getTransId(), new BencodedString(neighborId), "caojian", peers);
		sendKrpcPacket(node, resp);
	}

	private void handleAnnouncePeerQuery(DatagramPacket packet, KRPC krpc) throws IOException {
		BencodedMap reqData = (BencodedMap) krpc.getData().get(KRPC.QUERY_ARGS);
		BencodedString infohash = (BencodedString) reqData.get(KRPC.INFO_HASH);

		int port = reqData.get(KRPC.PORT).asLong().intValue();
		if (reqData.containsKey(KRPC.IMPLIED_PORT) && reqData.get(KRPC.IMPLIED_PORT).asLong() != 0) {
			port = packet.getPort();
		}

		Peer peer = new Peer(packet.getAddress(), port);
		metaWatcher.onAnnouncePeer(infohash, peer);

		KRPC resp = KRPC.buildAnnouncePeerRespPacket(krpc.getTransId(), nodeId);
		Node node = new Node(krpc.getId(), packet.getAddress(), packet.getPort());
		sendKrpcPacket(node, resp);
	}

	private void handleResponse(DatagramPacket packet, KRPC krpc) {
		Transaction transaction = transactionManager.getTransaction(krpc.getTransId());
		if (transaction == null) {
			LOGGER.debug("transaction not exist, node:{}:{}, krpc:{}", packet.getAddress(), packet.getPort(), krpc);
			return;
		}

		switch (transaction.getKrpc().action()) {
			case PING:
				// ignore
				break;
			case FIND_NODE:
				handleFindNodeResponse(krpc);
				break;
			case GET_PEERS:
				break;
			case ANNOUNCE_PEER:
				break;
			default:
				LOGGER.warn("unsupport krpc packet action, action:{}, packet:{}", transaction.getKrpc().action(), krpc);
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
				nodeManager.putNode(node);
			}
		}
	}

	private void handleResponseError(DatagramPacket packet, KRPC krpc) {
		LOGGER.debug("recive error resp, from: {}:{}, packet:{}", packet.getAddress(), packet.getPort(), krpc);
	}
}
