package com.killxdcj.aiyawocao.bittorrent.bencoding;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.commons.codec.binary.Hex;

public class BencodedString extends AbstractBencodedValue implements Comparable {

  private byte[] data;

  public BencodedString(byte[] data) {
    this.data = Arrays.copyOf(data, data.length);
  }

  public BencodedString(String str) {
    data = str.getBytes(DEFAULT_CHARSET);
  }

  public BencodedString(byte bytes[], int offset, int length) {
    data = Arrays.copyOfRange(bytes, offset, offset + length);
  }

  @Override
  public String asString() {
    return new String(data, DEFAULT_CHARSET);
  }

  @Override
  public byte[] asBytes() {
    return data;
  }

  @Override
  public Object toHuman() {
    return asString();
  }

  public byte[] serialize() {
    byte[] lengthBytes = String.valueOf(data.length).getBytes(DEFAULT_CHARSET);
    ByteBuffer byteBuffer = ByteBuffer.allocate(lengthBytes.length + 1 + data.length);
    byteBuffer.put(lengthBytes);
    byteBuffer.put(STRING_SPLIT);
    byteBuffer.put(data);
    return byteBuffer.array();
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(data);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BencodedString that = (BencodedString) o;

    return Arrays.equals(data, that.data);
  }

  @Override
  public String toString() {
    //        return new String(data, DEFAULT_CHARSET);
    try {
      return Hex.encodeHexString(data);
    } catch (Exception e) {
      return "";
    }
  }

  @Override
  public int compareTo(Object o) {
    BencodedString that = (BencodedString) o;
    if (data.length != that.data.length) {
      return data.length - that.data.length;
    }

    for (int i = 0; i < data.length; i++) {
      if (data[i] == that.data[i]) {
        continue;
      } else if ((0xff & data[i]) > (0xff & that.data[i])) {
        return 1;
      } else {
        return -1;
      }
    }

    return 0;
  }

  public String asHexString() {
    return Hex.encodeHexString(data);
  }

  public boolean startsWith(BencodedString target) {
    if (target.data.length > data.length) {
      return false;
    }

    for (int i = 0; i < target.data.length; i++) {
      if (data[i] != target.data[i]) {
        return false;
      }
    }

    return true;
  }
}
