package com.killxdcj.aiyawocao.meta.proxy;

import com.alibaba.fastjson.JSON;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.meta.manager.MetaManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpResuestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpResuestHandler.class);

	private MetaManager metaManager;
	private Pattern metaPattern = Pattern.compile("/meta/(\\w+)/(\\w+)");

	public HttpResuestHandler(MetaManager metaManager) {
		this.metaManager = metaManager;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
		Matcher matcher = metaPattern.matcher(msg.uri());
		if (!matcher.find()) {
			writeResponse(ctx, "unsupport url");
		}

		String infohash = matcher.group(1);
		String action = matcher.group(2);
		switch (action) {
			case "exist":
				handleExist(ctx, infohash);
				break;
			case "put":
				handlePut(ctx, infohash, msg.content());
				break;
			case "parse":
				handleParse(ctx, infohash);
				break;
			default:
				writeResponse(ctx, "unsupport action");
				break;
		}
	}

	private void handleExist(ChannelHandlerContext ctx, String infohash) {
		writeResponse(ctx, new HashMap<String, Object>(){{
			put("errno", 0);
			put("infohash", infohash);
			put("exist", metaManager.doesMetaExist(infohash));
		}});
	}

	private void handlePut(ChannelHandlerContext ctx, String infohash, ByteBuf metadata) {
		try {
			byte[] meta = new byte[metadata.readableBytes()];
			metadata.readBytes(meta);
			metaManager.put(infohash, meta);
			writeResponse(ctx, new HashMap<String, Object>() {{
				put("errno", 0);
				put("errmsg", "successed");
			}});
			LOGGER.info("meta saved, {} -> {} bytes", infohash, meta.length);
		} catch (Exception e) {
			LOGGER.error("save meta error, " + infohash, e);
			writeResponse(ctx, new HashMap<String, Object>() {{
				put("errno", -1);
				put("errmsg", e.getMessage());
			}});
		}
	}

	private void handleParse(ChannelHandlerContext ctx, String infohash) {
		try {
			Bencoding bencoding = new Bencoding(metaManager.get(infohash));
			writeResponse(ctx, new HashMap<String, Object>() {{
				put("errno", 0);
				put("infohash", infohash);
				put("info", bencoding.decode().toHuman());
			}});
		} catch (Exception e) {
			writeResponse(ctx, new HashMap<String, Object>() {{
				put("errno", 0);
				put("infohash", infohash);
				put("errmsg", e.getMessage());
			}});
		}
	}

	private void writeResponse(ChannelHandlerContext ctx, Object obj) {
		DefaultFullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
						Unpooled.copiedBuffer(JSON.toJSONString(obj), CharsetUtil.UTF_8));
		resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");
		resp.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, resp.content().readableBytes());
//		resp.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
		ctx.writeAndFlush(resp);
	}
}
