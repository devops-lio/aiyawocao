package com.killxdcj.aiyawocao.bittorrent.bencoding;

import java.util.List;
import java.util.Map;

public interface IBencodedValue {
	String asString();

	byte[] asBytes();

	Long asLong();

	List<IBencodedValue> asList();

	Map<String, IBencodedValue> asMap();

	byte[] serialize();

	Object toHuman();
}
