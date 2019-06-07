package com.killxdcj.aiyawocao.common.utils;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.binary.Hex;

public class InfohashUtils {

  public static String encode(String infohash) throws Exception {
    StringBuffer sb = new StringBuffer(26);
    byte[] bytes = Hex.decodeHex(infohash.toCharArray());

    for (int i = 0; i <= 25; i++) {
      int start_bit_idx = 6 * i;
      int start_byte = bytes[start_bit_idx / 8];
      int start_bit_idx_in_byte = start_bit_idx % 8;

      if (start_bit_idx_in_byte == 0) {
        sb.append(dig2char(((int) start_byte & 0xFF) >> 2));
      } else if (start_bit_idx_in_byte == 6) {
        sb.append(dig2char(((start_byte & 0x03) << 4) | (((int) bytes[start_bit_idx / 8 + 1] & 0xFF) >> 4)));
      } else if (start_bit_idx_in_byte == 4) {
        sb.append(dig2char(((start_byte & 0x0F) << 2) | (((int) bytes[start_bit_idx / 8 + 1] & 0xFF) >> 6)));
      } else if (start_bit_idx_in_byte == 2) {
        sb.append(dig2char(start_byte & 0x3F));
      }
    }
    sb.append(dig2char((bytes[19] & 0x0F) << 2));
    return sb.toString();
  }

  public static String decode(String data) throws Exception {
    byte[] bytes = new byte[20];
    for (int i = 0; i < 20; i++) {
      int start_bit_idx = 8 * i;
      char start_char = data.charAt(start_bit_idx / 6);
      int start_bit_idx_in_char = start_bit_idx % 6;

      if (start_bit_idx_in_char == 0) {
        bytes[i] = (byte) ((char2dig(start_char) << 2) | char2dig(data.charAt(start_bit_idx / 6 + 1)) >> 4);
      } else if (start_bit_idx_in_char == 2) {
        bytes[i] = (byte) ((char2dig(start_char) << 4) | char2dig(data.charAt(start_bit_idx / 6 + 1)) >> 2);
      } else if (start_bit_idx_in_char == 4) {
        bytes[i] = (byte) ((char2dig(start_char) << 6) | char2dig(data.charAt(start_bit_idx / 6 + 1)));
      }
    }
    return Hex.encodeHexString(bytes);
  }

  private static char dig2char(int dig) throws Exception {
    if (dig < 10) {
      // 0 -> 9, 0 ascii is 48
      return (char) (48 + dig);
    } else if (dig < 36) {
      // A -> Z, A ascii is 65
      return (char) (65 + dig - 10);
    } else if (dig < 62) {
      // a -> z a ascii is 97
      return (char) (97 + dig - 36);
    } else if (dig == 62) {
      return '-';
    } else if (dig == 63) {
      return '_';
    }

    throw new Exception("Unexecpet dig: " + dig);
  }

  private static int char2dig(char ch) throws Exception {
    if (48 <= ch && ch <= 57) {
      // 0 -> 10
      return ch - 48;
    } else if (65 <= ch && ch <= 90) {
      // A -> Z
      return 10 + ch - 65;
    } else if (97 <= ch && ch <= 122) {
      // a -> z
      return 36 + ch - 97;
    } else if (ch == '-') {
      return 62;
    } else if (ch == '_') {
      return 63;
    }

      throw new Exception("Unexpected char: " + ch);
  }

  public static void main(String[] args) {
    List<String> infohashs = new ArrayList(){{
      add("ca128a5de113fdb2c1a800970e81cf5a67bf8760");
      add("998852b38aadb2a6cbbbcf1f5691eabae0a8e48c");
      add("43ef1a8b4dd838d84546f040f7b1cb8615168958");
      add("0000000000000000000000000000000000000000");
      add("ffffffffffffffffffffffffffffffffffffffff");
    }};

    for (String infohash : infohashs) {
      try {
        String c = InfohashUtils.encode(infohash);
        String dc = InfohashUtils.decode(c);
        if (infohash.equals(dc)) {
          System.out.println("eq " + infohash + " -> " + c);
        } else {
          System.out.println("not eq" + infohash + " -> " + dc);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
