package com.killxdcj.aiyawocao.bittorrent.bencoding;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BencodedMap extends AbstractBencodedValue {
  private Map<String, IBencodedValue> data;

  public BencodedMap() {
    data = new HashMap();
  }

  public BencodedMap(Map<String, IBencodedValue> data) {
    this.data = data;
  }

  public void put(String key, IBencodedValue value) {
    data.put(key, value);
  }

  public IBencodedValue get(String key) {
    return data.get(key);
  }

  public void remove(String key) {
    data.remove(key);
  }

  public boolean containsKey(String key) {
    return data.containsKey(key);
  }

  @Override
  public Map<String, IBencodedValue> asMap() {
    return data;
  }

  @Override
  public Object toHuman() {
    Map<String, Object> ret = new HashMap<>();
    Map<String, Object> utf8KV = new HashMap<>();
    for (Map.Entry<String, IBencodedValue> entry : data.entrySet()) {
      String key = entry.getKey();
      if (key.equals("pieces") || key.equals("ed2k") || key.equals("filehash") || key.equals("")
          || key.equals("piece length") || key.equals("sha1") || key.equals("file-media")
          || key.equals("file-duration") || key.equals("hash")) {
        // ignore
      } else {
        Object value = entry.getValue().toHuman();
        if ((key.equals("path") || key.equals("path.utf-8")) && value instanceof List) {
          value = String.join("/", (List)value);
        }
//        else if (key.equals("files") && value instanceof List) {
//          List<String> files = new ArrayList<>();
//          for (Map<String, Object> file : (List<Map<String, Object>>) value) {
//            files.add(file.get("path") + ":" + file.get("length"));
//          }
//          value = files;
//        }

        if (key.endsWith(".utf-8")) {
          utf8KV.put(key.replace(".utf-8", ""), value);
        } else if (key.endsWith(".utf8")) {
          utf8KV.put(key.replace(".utf8", ""), value);
        }else {
          ret.put(key, value);
        }
      }
    }
    ret.putAll(utf8KV);
    return ret;
  }

  public byte[] serialize() {
    int totalLength = 2;
    Map<byte[], byte[]> dataBytes = new HashMap();
    for (Map.Entry<String, IBencodedValue> entry : data.entrySet()) {
      byte[] keyBytes = new BencodedString(entry.getKey()).serialize();
      byte[] valueBytes = entry.getValue().serialize();
      dataBytes.put(keyBytes, valueBytes);
      totalLength += keyBytes.length;
      totalLength += valueBytes.length;
    }

    ByteBuffer byteBuffer = ByteBuffer.allocate(totalLength);
    byteBuffer.put(MAP_ENTRY);
    for (Map.Entry<byte[], byte[]> entry : dataBytes.entrySet()) {
      byteBuffer.put(entry.getKey());
      byteBuffer.put(entry.getValue());
    }
    byteBuffer.put(END_BYTE);
    return byteBuffer.array();
  }

  @Override
  public int hashCode() {
    return data != null ? data.hashCode() : 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BencodedMap that = (BencodedMap) o;

    return data != null ? data.equals(that.data) : that.data == null;
  }

  @Override
  public String toString() {
    return "{" + data + "}";
  }
}
