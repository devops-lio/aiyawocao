package com.killxdcj.aiyawocao.bittorrent.peer;

import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedInteger;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedMap;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.utils.JTorrentUtils;
import com.killxdcj.aiyawocao.bittorrent.utils.TimeUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class MetaFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(MetaFetcher.class);

	private static final byte[] handshakePrefix = buildHandshakePacketPrefix();
	private static final byte[] extHandshake = buildExtHandshake();
	private byte[] peerId = JTorrentUtils.genPeerId().asBytes();

	private static final byte EXTENDED = (byte) 20;
	private static final byte HANDSHAKE = (byte) 0;
	private static final byte UT_METADATA = (byte) 1;
	private static final int BLOCK_SIZE = 16 * 1024;
	private static final int REQUEST = 0;
	private static final int DATA = 1;
	private static final int REJECT = 2;

	private byte remoteUtMetadataId;
	private int meatadataSize;
	private int pieceTotal;
	private Map<Integer, byte[]> metadataTable = new HashMap<>();

	private BencodedString infohash;
	private Peer peer;
	private MetaFetchWatcher watcher;
	private SocketChannel channel;
	private SelectionKey selectionKey;
	private long startTime;

	private FetchProgress curProgress;
	private ReadProgress readProgress;
	private ByteBuffer packetToWrite;
	private ByteBuffer packetToRead;

	private boolean successed = false;
	private Throwable t = null;
	private byte[] metadata;
	private AtomicBoolean notifyed = new AtomicBoolean(false);

	public MetaFetcher(BencodedString infohash, Peer peer, MetaFetchWatcher watcher) {
		this.infohash = infohash;
		this.peer = peer;
		this.watcher = watcher;
	}

	public void start(Selector selector) throws IOException {
		startTime = TimeUtils.getCurTime();
		channel = SocketChannel.open();
		channel.configureBlocking(false);
		channel.connect(new InetSocketAddress(peer.getAddr(), peer.getPort()));
		selectionKey = channel.register(selector, SelectionKey.OP_CONNECT);
		selectionKey.attach(this);
		curProgress = FetchProgress.CONNECT;
	}

	public boolean execute() {
		boolean finished = false;
		try {
			if (selectionKey.isConnectable()) {
				onConnectAble();
			} else if (selectionKey.isWritable()) {
				onWriteAble();
			} else if (selectionKey.isReadable()) {
				onReadAble();
			}
			finished = successed;
		} catch (Exception e) {
			t = e;
			finished = true;
		}

		return finished;
	}

	private void onConnectAble() throws IOException {
		channel.finishConnect();
		selectionKey.interestOps(SelectionKey.OP_WRITE);
		curProgress = FetchProgress.SEND_HANDSHAKE;
		packetToWrite = buildHandShakePacket();
		LOGGER.debug("peer connected, {}", this);
	}

	private void onReadAble() throws Exception {
		if (fillinCurrentPacket()) {
			if (curProgress == FetchProgress.READ_HANDSHAKE) {
				byte[] handshake = packetToRead.array();
				if (!Arrays.equals(Arrays.copyOf(handshakePrefix, 20), Arrays.copyOf(handshake, 20))
								|| (handshake[25] & 0x10) == 0) {
					throw new Exception("Handshake error, prefix data error");
				}

				selectionKey.interestOps(SelectionKey.OP_WRITE);
				curProgress = FetchProgress.SEND_EXT_HANDSHAKE;
				packetToWrite = ByteBuffer.wrap(extHandshake);
				LOGGER.debug("handshake successed, {}", this);
			} else if (curProgress == FetchProgress.EXT_EXCHANGE) {
				do {
					ByteBuffer packet = readPacket();
					if (packet != null) {
						LOGGER.debug("recive exchange packet, {}, {}", Arrays.toString(packet.array()), this);

						byte extended = packet.get();
						if (extended != EXTENDED) {
							LOGGER.warn("get unknow extended packet, extended:{}", extended);
						} else {
							byte msgType = packet.get();
							switch (msgType) {
								case HANDSHAKE:
									handleExtHandshake(packet);
									LOGGER.debug("exthandshake successed, {}, metasize:{}, pieces:{}", this, meatadataSize, pieceTotal);
									break;
								case UT_METADATA:
									LOGGER.debug("recive utmetadata, {}, {}", Arrays.toString(packet.array()), this);
									handleUtMetadata(packet);
									break;
								default:
									LOGGER.warn("unknow ext msgtype:{}", msgType);
							}
						}
					}
				} while (!successed && fillinCurrentPacket());
			}
		}
	}

	private boolean fillinCurrentPacket() throws Exception {
		if (channel.read(packetToRead) <= -1) {
			throw new Exception("read EOF");
		}

		boolean full = !packetToRead.hasRemaining();
		if (full) {
			packetToRead.flip();
		}
		return full;
	}

	private void onWriteAble() throws IOException {
		channel.write(packetToWrite);
		if (!packetToWrite.hasRemaining()) {
			packetToWrite = null;
			if (curProgress == FetchProgress.SEND_HANDSHAKE) {
				selectionKey.interestOps(SelectionKey.OP_READ);
				curProgress = FetchProgress.READ_HANDSHAKE;
				packetToRead = ByteBuffer.allocate(68);
				LOGGER.debug("handshake sended, {}", this);
			} else if (curProgress == FetchProgress.SEND_EXT_HANDSHAKE) {
				selectionKey.interestOps(SelectionKey.OP_READ);
				curProgress = FetchProgress.EXT_EXCHANGE;
				readProgress = ReadProgress.READ_LENGTH;
				packetToRead = ByteBuffer.allocate(4);
				LOGGER.debug("exthandshake sended, {}", this);
			} else if (curProgress == FetchProgress.SEND_PIECES_REQ) {
				selectionKey.interestOps(SelectionKey.OP_READ);
				curProgress = FetchProgress.EXT_EXCHANGE;
				readProgress = ReadProgress.READ_LENGTH;
				packetToRead = ByteBuffer.allocate(4);
				LOGGER.debug("pieces request sended, {}", this);
			}
		}
	}

	private ByteBuffer readPacket() {
		ByteBuffer packet = null;
		if (readProgress == ReadProgress.READ_LENGTH) {
			int bodylength = packetToRead.getInt();
			if (bodylength == 0) {
				packetToRead = ByteBuffer.allocate(4);
			} else {
				packetToRead = ByteBuffer.allocate(bodylength);
				readProgress = ReadProgress.READ_BODY;
			}
		} else if (readProgress == ReadProgress.READ_BODY) {
			packet = packetToRead;
			packetToRead = ByteBuffer.allocate(4);
			readProgress = ReadProgress.READ_LENGTH;
		}

		return packet;
	}

	private void handleExtHandshake(ByteBuffer packet) throws Exception {
		Bencoding bencoding = new Bencoding(Arrays.copyOfRange(packet.array(), 2, packet.array().length));
		BencodedMap bencodedMap = (BencodedMap) bencoding.decode();
		if (!bencodedMap.containsKey("m") || !bencodedMap.containsKey("metadata_size")) {
			throw new Exception("ExtHandshake packet miss m or metadata_size, " + bencodedMap.toString());
		}

		remoteUtMetadataId = ((BencodedMap) bencodedMap.get("m")).get("ut_metadata").asLong().byteValue();
		meatadataSize = bencodedMap.get("metadata_size").asLong().intValue();
		if (meatadataSize % BLOCK_SIZE > 0) {
			pieceTotal = meatadataSize / BLOCK_SIZE + 1;
		}

		List<ByteBuffer> reqs = new ArrayList<>(pieceTotal);
		int totalLength = 0;
		for (int i = 0; i < pieceTotal; i++) {
			BencodedMap req = new BencodedMap();
			req.put("msg_type", new BencodedInteger(REQUEST));
			req.put("piece", new BencodedInteger(i));
			ByteBuffer reqPacket = buildPacket(remoteUtMetadataId, req.serialize());
			reqs.add(reqPacket);
			totalLength += reqPacket.array().length;
		}
		byte[] reqsBytes = new byte[totalLength];
		int idx = 0;
		for (ByteBuffer req : reqs) {
			System.arraycopy(req.array(), 0, reqsBytes, idx, req.array().length);
			idx += req.array().length;
		}
		packetToWrite = ByteBuffer.wrap(reqsBytes);
		curProgress = FetchProgress.SEND_PIECES_REQ;
		selectionKey.interestOps(SelectionKey.OP_WRITE);
		LOGGER.debug("pieces request : {}", Arrays.toString(reqsBytes));
	}

	private void handleUtMetadata(ByteBuffer packet) throws Exception {
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
				byte[] data = Arrays.copyOfRange(packetBytes, 2 + bencoding.getCurIndex(), packetBytes.length);
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

				metadataTable.put(piece, data);
				LOGGER.info("fetched metadata piece, infohash:{}, total:{}, cur:{}, size:{}bytes",
								infohash.asHexString(), pieceTotal, piece, data.length);

				if (metadataTable.size() == pieceTotal) {
					ByteBuffer buf = ByteBuffer.allocate(meatadataSize);
					for (int i = 0; i < pieceTotal; i++) {
						buf.put(metadataTable.get(i));
					}

					if (!infohash.asHexString().equals(DigestUtils.sha1Hex(buf.array()))) {
						throw new Exception("fetched metadataTable, but sha1 is error");
					} else {
						metadata = buf.array();
						successed = true;
					}
				}
				break;
			case REJECT:
				throw new Exception("peer reject, it doesn't have piece:"
								+ bencodedMap.get("piece").asLong().intValue());
		}
	}

	private static byte[] buildHandshakePacketPrefix() {
		ByteBuffer packet = ByteBuffer.allocate(28);// 48 = 1 + 19 + 8 + 20
		packet.put((byte) 19);
		packet.put("BitTorrent protocol".getBytes());
		packet.put(new byte[]{(byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 16, (byte) 0, (byte) 1});
		return packet.array();
	}

	private static byte[] buildExtHandshake() {
		BencodedMap extHandshake = new BencodedMap();
		BencodedMap data = new BencodedMap();
		data.put("ut_metadata", new BencodedInteger(UT_METADATA));
		extHandshake.put("m", data);
		return buildPacket(HANDSHAKE, extHandshake.serialize()).array();
	}

	private static ByteBuffer buildPacket(byte extendedId, byte[] payload) {
		int length = 4 + 2 + payload.length;
		ByteBuffer ret = ByteBuffer.allocate(length);
		ret.putInt(2 + payload.length);
		ret.put(EXTENDED);
		ret.put(extendedId);
		ret.put(payload);
		ret.flip();
		return ret;
	}

	private ByteBuffer buildHandShakePacket() {
		byte[] packet = new byte[28 + 20 + 20];
		System.arraycopy(handshakePrefix, 0, packet, 0, 28);
		System.arraycopy(infohash.asBytes(), 0, packet, 28, 20);
		System.arraycopy(peerId, 0, packet, 48, 20);
		return ByteBuffer.wrap(packet);
	}

	public void notifyWatcher() {
		try {
			selectionKey.cancel();
			channel.close();
		} catch (IOException e) {
		}

		if (successed) {
			watcher.onSuccessed(infohash, peer, metadata, TimeUtils.getElapseTime(startTime));
		} else if (t != null) {
			watcher.onException(infohash, peer, t, TimeUtils.getElapseTime(startTime));
		} else {
			watcher.onException(infohash, peer, new TimeoutException("meta fetch timeout"), TimeUtils.getElapseTime(startTime));
		}
	}

	public long getElapseTime() {
		return TimeUtils.getElapseTime(startTime);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MetaFetcher fetcher = (MetaFetcher) o;

		if (infohash != null ? !infohash.equals(fetcher.infohash) : fetcher.infohash != null) return false;
		return peer != null ? peer.equals(fetcher.peer) : fetcher.peer == null;
	}

	@Override
	public int hashCode() {
		int result = infohash != null ? infohash.hashCode() : 0;
		result = 31 * result + (peer != null ? peer.hashCode() : 0);
		return result;
	}

	private enum FetchProgress {
		CONNECT,
		SEND_HANDSHAKE,
		READ_HANDSHAKE,
		SEND_EXT_HANDSHAKE,
		SEND_PIECES_REQ,
		EXT_EXCHANGE
	}

	private enum  ReadProgress {
		READ_LENGTH,
		READ_BODY
	}

	@Override
	public String toString() {
		return infohash.asHexString() + ", " + peer;
	}

	public Peer getPeer() {
		return peer;
	}

	public boolean getResult() {
		if (successed) {
			return successed;
		}

		return false;
	}

	public boolean markNotify() {
		if (notifyed.compareAndSet(false, true)) {
			return true;
		}
		return false;
	}
}
