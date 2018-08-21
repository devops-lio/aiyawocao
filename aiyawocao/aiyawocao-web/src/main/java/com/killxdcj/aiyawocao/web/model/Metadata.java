package com.killxdcj.aiyawocao.web.model;

import com.alibaba.fastjson.JSON;
import com.killxdcj.aiyawocao.common.utils.CommonUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javafx.util.Pair;

public class Metadata {

  private static final String[] UNITS = {"Bytes", "KB", "MB", "GB", "TB"};
  private static final String NAME_WITH_SIZE_FMT = "<span style='color:red;margin-right:0px;"
      + "padding: 3px;'>%s</span>&nbsp;&nbsp;<span style='color:blue;margin-right:0px;"
      + "background-color:lawngreen;border:1px solid;border-radius:10px;padding:2px;'>%s</span>";
  private static int DEFAULT_DIGEST_FILES_SIZE = 10;

  private Map<String, Object> originalData;

  private long totalLength;

  public Metadata(Map<String, Object> originalData) {
    this.originalData = originalData;
  }

  public Map<String, Object> getOriginalData() {
    return originalData;
  }

  public String getName() {
    return (String) originalData.get("name");
  }

  public String getDate() {
    return (String) originalData.get("date");
  }

  public String getInfohash() {
    return (String) originalData.get("infohash");
  }

  public int getPopularity() {
    return new Random().nextInt(1000);
  }

  public String getHumanFileSize() {
    long totalSize = 0;
    if (originalData.containsKey("files")) {
      if (originalData.containsKey("files")) {
        for (Map<String, String> file : (List<Map<String, String>>) originalData.get("files")) {
          totalSize += Long.parseLong(file.get("length"));
        }
      } else {
        totalSize += Long.parseLong((String) originalData.get("length"));
      }
    }
    return CommonUtils.fileSize2Human(totalSize);
  }

  public int getFileNum() {
    if (originalData.containsKey("files")) {
      return ((List<Map<String, String>>) originalData.get("files")).size();
    } else {
      return 1;
    }
  }

  public List<Pair<String, Long>> getDigestFiles() {
    return getDigestFiles(DEFAULT_DIGEST_FILES_SIZE);
  }

  public List<Pair<String, Long>> getDigestFiles(int digestFilesSize) {
    Pair<String, String> x = new Pair<>("xx", "xx");
    List<Pair<String, Long>> ret = new ArrayList<>();
    if (originalData.containsKey("files")) {
      List<Map<String, String>> files = (List<Map<String, String>>) originalData.get("files");
      for (int i = 0; i < (files.size() > digestFilesSize ? digestFilesSize : files.size()); i++) {
        Map<String, String> file = files.get(i);
        if (file.get("path").indexOf("请升级到BitComet") != -1) {
          continue;
        }
        String[] tmps = file.get("path").split("/");
        ret.add(new Pair(tmps[tmps.length - 1],
            CommonUtils.fileSize2Human(Long.parseLong(file.get("length")))));
      }
    } else {
      ret.add(new Pair(originalData.get("name"),
          CommonUtils.fileSize2Human(Long.parseLong((String) originalData.get("length")))));
    }
    return ret;
  }

  public String getFileTree() {
    Map<String, Object> root = new HashMap<>();
    root.put("name", getName());

    Map<String, Map<String, Object>> nodeMap = new HashMap<>();
    if (originalData.containsKey("files")) {
      boolean needOpen = true;
      for (Map<String, String> file : (List<Map<String, String>>) originalData.get("files")) {
        long length = Long.parseLong(file.get("length"));
        totalLength += length;

        if (file.get("path").indexOf("请升级到BitComet") != -1) {
          continue;
        }
        String[] paths = file.get("path").split("/");

        String parentPath = "";
        for (int i = 0; i < paths.length; i++) {
          Map<String, Object> parent = parentPath.length() == 0 ? root : nodeMap.get(parentPath);
          parentPath += paths[i];
          if (!nodeMap.containsKey(parentPath)) {
            Map<String, Object> me = new HashMap<>();
            if (i == paths.length - 1) {
              me.put("name",
                  String.format(NAME_WITH_SIZE_FMT, paths[i], CommonUtils.fileSize2Human(length)));
            } else {
              me.put("name", paths[i]);
            }
            nodeMap.put(parentPath, me);
            if (!parent.containsKey("children")) {
              parent.put("children", new ArrayList<>());
              if (needOpen) {
                parent.put("open", true);
                needOpen = false;
              }
            }
            ((List) parent.get("children")).add(me);
          }
        }
      }
    }
    return JSON.toJSONString(root);
  }
}
