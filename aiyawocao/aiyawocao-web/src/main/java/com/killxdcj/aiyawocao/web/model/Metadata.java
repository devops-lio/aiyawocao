package com.killxdcj.aiyawocao.web.model;

import com.alibaba.fastjson.JSON;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Metadata {
  private static final String[] UNITS = {"Bytes", "KB", "MB", "GB", "TB"};
  private static final String NAME_WITH_SIZE_FMT = "<span style='color:red;margin-right:0px;"
      + "padding: 3px;'>%s</span>&nbsp;&nbsp;<span style='color:blue;margin-right:0px;"
      + "background-color:lawngreen;border:1px solid;border-radius:10px;padding:2px;'>%s</span>";
  private Map<String, Object> originalData;

  private long totalLength;

  public Metadata(Map<String, Object> originalData) {
    this.originalData = originalData;
  }

  private String getDate() {
    return (String)originalData.get("date");
  }

  private String getName() {
    return (String)originalData.get("name");
  }

  private String getInfohash() {
    return (String)originalData.get("infohash");
  }

  public String getFileTree() {
    Map<String, Object> root = new HashMap<>();
    root.put("name", getName());

    Map<String, Map<String, Object>> nodeMap = new HashMap<>();
    if (originalData.containsKey("files")) {
      boolean needOpen = true;
      for(Map<String, String> file : (List<Map<String, String>>)originalData.get("files")) {
        long length = Long.parseLong(file.get("length"));
        totalLength += length;
        String[] paths = file.get("path").split("/");

        String parentPath = "";
        for (int i = 0; i < paths.length; i++) {
          Map<String, Object> parent = parentPath.length() == 0 ? root : nodeMap.get(parentPath);
          parentPath += paths[i];
          if (!nodeMap.containsKey(parentPath)) {
            Map<String, Object> me = new HashMap<>();
            if (i == paths.length - 1) {
              me.put("name", String.format(NAME_WITH_SIZE_FMT, paths[i], transLength(length)));
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
            ((List)parent.get("children")).add(me);
          }
        }
      }
    }
    return JSON.toJSONString(root);
  }

  private String transLength(long length) {
    double humanLength = length;
    int unitIdx = 0;
    while (humanLength > 1024 && unitIdx < 5) {
      humanLength = humanLength / 1024;
      unitIdx++;
    }

    return String.format("%.2f %s", humanLength, UNITS[unitIdx]);
  }
}
