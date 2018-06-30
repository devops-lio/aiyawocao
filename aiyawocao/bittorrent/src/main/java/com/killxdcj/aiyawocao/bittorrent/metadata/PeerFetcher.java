package com.killxdcj.aiyawocao.bittorrent.metadata;

import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedInteger;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedMap;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.peer.Peer;
import com.killxdcj.aiyawocao.bittorrent.utils.JTorrentUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class PeerFetcher {
	private static final Logger LOGGER = LoggerFactory.getLogger(PeerFetcher.class);

	private Peer peer;
	private PeerTaskManager peerTaskManager;
	private EventLoopGroup eventLoopGroup;
	private ExecutorService executorService;
	ByteBuf buffer = Unpooled.buffer();

	public PeerFetcher(Peer peer, PeerTaskManager peerTaskManager, EventLoopGroup eventLoopGroup, ExecutorService executorService) {
		this.peer = peer;
		this.peerTaskManager = peerTaskManager;
		this.eventLoopGroup = eventLoopGroup;
		this.executorService = executorService;

		startNextTask();
	}

	private void startNextTask() {
		Task nextTask = peerTaskManager.getNextTask(peer);
		if (nextTask == null) {
			return;
		}

		Fetcher fetcher = new Fetcher(nextTask);
		Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(eventLoopGroup)
						.channel(NioSocketChannel.class)
						.option(ChannelOption.TCP_NODELAY, true)
						.handler(new ChannelInitializer<SocketChannel>() {
							@Override
							protected void initChannel(SocketChannel ch) throws Exception {
								ChannelPipeline p = ch.pipeline();
								p.addLast(new ReadTimeoutHandler(300));
								p.addLast(fetcher);
							}
						})
						.connect(peer.getAddr(), peer.getPort())
						.addListener(fetcher);
		LOGGER.info("fetcher started, {}", nextTask);
	}

	private class Fetcher extends ChannelInboundHandlerAdapter implements GenericFutureListener {
		private Task task;
		private volatile boolean successed = false;
		private AtomicBoolean notifyed = new AtomicBoolean(false);
		private volatile boolean handshaked = false;
		private volatile Throwable t = null;

		private byte remoteUtMetadataId;
		private int meatadataSize;
		private int pieceTotal;
		private Map<Integer, byte[]> metadata = new HashMap<>();

		public Fetcher(Task task) {
			this.task = task;
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			ctx.writeAndFlush(buildHandShakePacket(JTorrentUtils.genNodeId(), task.getInfohash()));
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			LOGGER.debug("connection closed, {}", peer);
			ctx.close();
			finish();
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
			LOGGER.debug("recive data, {}, {}bytes", peer, ((ByteBuf)msg).readableBytes());
			buffer.writeBytes((ByteBuf)msg);
			try {
				handlePacket(ctx);
			} catch (Throwable t) {
				this.t = t;
			}

			if (t != null || successed) {
				ctx.close();
				finish();
			}
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			LOGGER.debug("connection exception, " + peer, cause);
			ctx.close();
			t = cause;
			finish();
		}

		@Override
		public void operationComplete(Future future) throws Exception {
			LOGGER.debug("connect to peer failed, {}", peer);
			if (!future.isSuccess()) {
				if (future.cause() != null) {
					t = future.cause();
				} else {
					t = new Exception("connect to peer failed, " + peer);
				}
				finish();
			} else {
				LOGGER.debug("connect to peer successed, {}", peer);
			}
		}

		private void handlePacket(ChannelHandlerContext ctx) throws Exception {
			if (!handshaked) {
				if (buffer.readableBytes() < 68) {
					return;
				}

				ByteBuf resp = buffer.readBytes(68);
				if (!task.getInfohash().equals(new BencodedString(Arrays.copyOfRange(bytebuf2array(resp), 28, 48)))) {
					t = new Exception("handshakepredix verify error");
				}

				handshaked = true;
				ctx.writeAndFlush(Unpooled.copiedBuffer(extHandshake));
			} else {
				for (ByteBuf packet = readPacket(); packet != null; packet = readPacket()) {
					if (packet.readableBytes() == 0) {
						// keepalive ignore
						continue;
					}

					byte extended = packet.readByte();
					if (extended != EXTENDED) {
						LOGGER.warn("unknow extended: {}", extended);
					}

					byte msgType = packet.readByte();
					switch (msgType) {
						case HANDSHAKE:
							onHandShake(packet, ctx);
							break;
						case UT_METADATA:
							onUtMetadata(packet);
							break;
						default:
							LOGGER.warn("unknow megtype: {}", msgType);
					}
				}
			}
		}

		private void onHandShake(ByteBuf packet, ChannelHandlerContext ctx) throws Exception {
			Bencoding bencoding = new Bencoding(bytebuf2array(packet));
			BencodedMap bencodedMap = (BencodedMap) bencoding.decode();
			if (!bencodedMap.containsKey("m") || !bencodedMap.containsKey("metadata_size")) {
				throw new Exception("invalid exthandshake, m and metadata_size is needed, " + bencodedMap.toString());
			}

			remoteUtMetadataId = ((BencodedMap) bencodedMap.get("m")).get("ut_metadata").asLong().byteValue();
			meatadataSize = bencodedMap.get("metadata_size").asLong().intValue();
			if (meatadataSize % BLOCK_SIZE > 0) {
				pieceTotal = meatadataSize / BLOCK_SIZE + 1;
			}

			for (int i = 0; i < pieceTotal; i++) {
				BencodedMap req = new BencodedMap();
				req.put("msg_type", new BencodedInteger(REQUEST));
				req.put("piece", new BencodedInteger(i));
				ctx.writeAndFlush(buildPacket(remoteUtMetadataId, req.serialize()));
			}
		}

		private void onUtMetadata(ByteBuf packet) throws Exception {
			byte[] packetBytes = bytebuf2array(packet);
			Bencoding bencoding = new Bencoding(packetBytes);
			BencodedMap bencodedMap = (BencodedMap) bencoding.decode();

			int msgType = bencodedMap.get("msg_type").asLong().intValue();
			switch (msgType) {
				case DATA:
					int piece = bencodedMap.get("piece").asLong().intValue();
					byte[] data = Arrays.copyOfRange(packetBytes, bencoding.getCurIndex(), packetBytes.length);
					if (piece > pieceTotal - 1) {
						throw new Exception("piece outof range");
					}

					if (pieceTotal == 1) {
						if (meatadataSize != data.length) {
							throw new Exception("piece size not match, expect:" + meatadataSize + ", real:" + data.length);
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
					LOGGER.info("fetched metadata piece, infohash:{}, total:{}, cur:{}, size:{}bytes",
									task.getInfohash().asHexString(), pieceTotal, piece, data.length);

					if (metadata.size() == pieceTotal) {
						successed = true;
					}
					break;
				case REJECT:
					throw new Exception("peer reject, it doesn't have piece:" + bencodedMap.get("piece").asLong().intValue());
				default:
					LOGGER.warn("unhandled utmetadata packet type, {}", msgType);
			}
		}

		private ByteBuf readPacket() {
			if (buffer.readableBytes() < 4) {
				return null;
			}

			buffer.markReaderIndex();
			int length = buffer.readInt();
			if (buffer.readableBytes() < length) {
				buffer.resetReaderIndex();
				return null;
			}

			return buffer.readBytes(length);
		}

		private void finish() {
			if (!notifyed.compareAndSet(false, true)) {
				return;
			}

			startNextTask();

			if (successed) {
				String infohashHex = task.getInfohash().asHexString();
				if (metadata.size() == pieceTotal) {
					ByteBuf buf = Unpooled.buffer(meatadataSize);
					for (int i = 0; i < pieceTotal; i++) {
						buf.writeBytes(metadata.get(i));
					}

					byte[] data = bytebuf2array(buf);
					if (!infohashHex.equals(DigestUtils.sha1Hex(data))) {
						executorService.submit(() -> task.getListener().onFailed(task.getPeer(), task.getInfohash(),
										new Exception("fetched metadata, but sha1 is error")));
					} else {
						executorService.submit(() -> task.getListener().onSuccedded(task.getPeer(), task.getInfohash(), data));
					}
				}
			} else {
				task.getListener().onFailed(task.getPeer(), task.getInfohash(), t);
			}
		}
	}


	// utils
	private static final byte[] handshakePrefix = buildHandshakePacketPrefix();
	private static final byte[] extHandshake = buildExtHandshake();

	private static final byte EXTENDED = (byte) 20;
	private static final byte HANDSHAKE = (byte) 0;
	private static final byte UT_METADATA = (byte) 1;
	private static final int BLOCK_SIZE = 16 * 1024;
	private static final int REQUEST = 0;
	private static final int DATA = 1;
	private static final int REJECT = 2;

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
		return bytebuf2array(buildPacket(HANDSHAKE, extHandshake.serialize()));
	}

	private static ByteBuf buildPacket(byte extendedId, byte[] payload) {
		int length = 4 + 2 + payload.length;
		ByteBuf ret = Unpooled.buffer(length);
		ret.writeInt(2 + payload.length);
		ret.writeByte(EXTENDED);
		ret.writeByte(extendedId);
		ret.writeBytes(payload);
		return ret;
	}

	private static ByteBuf buildHandShakePacket(BencodedString peerId, BencodedString infohash) {
		ByteBuf ret = Unpooled.buffer(28 + 20 + 20);
		ret.writeBytes(handshakePrefix);
		ret.writeBytes(infohash.asBytes());
		ret.writeBytes(peerId.asBytes());
		return ret;
	}

	private static byte[] bytebuf2array(ByteBuf buf) {
		int length = buf.readableBytes();
		byte[] ret = new byte[length];
		buf.readBytes(ret);
		return ret;
	}
}
