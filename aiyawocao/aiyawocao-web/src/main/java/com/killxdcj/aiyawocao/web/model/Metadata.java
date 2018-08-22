package com.killxdcj.aiyawocao.web.model;

import com.killxdcj.aiyawocao.common.utils.CommonUtils;
import com.killxdcj.aiyawocao.web.utils.WebUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javafx.util.Pair;

public class Metadata {

  private static final String[] UNITS = {"Bytes", "KB", "MB", "GB", "TB"};
  private static final String NAME_WITH_SIZE_FMT =
      "<span style='color:red;margin-right:0px;"
          + "padding: 3px;'>%s</span>&nbsp;&nbsp;<span style='color:blue;margin-right:0px;"
          + "background-color:lawngreen;border:1px solid;border-radius:10px;padding:2px;'>%s</span>";
  private static int DEFAULT_DIGEST_FILES_SIZE = 10;

  private Map<String, Object> originalData;

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

  public String getMagic() {
    return WebUtils.calcMagic(getInfohash());
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
        ret.add(
            new Pair(
                tmps[tmps.length - 1],
                CommonUtils.fileSize2Human(Long.parseLong(file.get("length")))));
      }
    } else {
      ret.add(
          new Pair(
              originalData.get("name"),
              CommonUtils.fileSize2Human(Long.parseLong((String) originalData.get("length")))));
    }
    return ret;
  }

  public String getFileTree() {
    Pair<String, Object> root;
    Map<String, Pair<String, Object>> dirNodeMap = new HashMap<>();
    if (originalData.containsKey("files")) {
      root = new Pair<>(getName(), new ArrayList<>());
      dirNodeMap.put(getName(), root);

      for (Map<String, String> file : (List<Map<String, String>>) originalData.get("files")) {
        if (file.get("path").indexOf("请升级到BitComet") != -1) {
          continue;
        }

        long length = Long.parseLong(file.get("length"));
        String[] paths = file.get("path").split("/");

        List<Pair<String, Object>> parentChilds = (List<Pair<String, Object>>) root.getValue();
        String fullPath = "";
        for (int i = 0; i < paths.length - 1; i++) {
          fullPath = fullPath + "/" + paths[i];
          if (dirNodeMap.containsKey(fullPath)) {
            parentChilds = (List<Pair<String, Object>>) dirNodeMap.get(fullPath).getValue();
            continue;
          }

          Pair<String, Object> curDir = new Pair<>(paths[i], new ArrayList<>());
          parentChilds.add(curDir);
          dirNodeMap.put(fullPath, curDir);
          parentChilds = (List<Pair<String, Object>>) curDir.getValue();
        }

        parentChilds.add(new Pair<>(paths[paths.length - 1], file.get("length")));
      }
    } else {
      root = new Pair<>(getName(), (String) originalData.get("length"));
    }

    return appendNode("", root, true);
  }

  private String appendNode(String parent, Pair<String, Object> node, boolean openFolder) {
    if (node.getValue() instanceof String) {
      String humanFileSize = CommonUtils.fileSize2Human(Long.parseLong((String) node.getValue()));
      parent =
          parent
              + "<li><i class=\"fa fa-file-video-o\" aria-hidden=\"true\"></i>&nbsp;"
              + node.getKey()
              + "<span class=\"detail-file-size\">"
              + humanFileSize
              + "</span></li>";
    } else {
      if (openFolder) {
        parent =
            parent
                + "<li><i class=\"fa fa-folder\" aria-hidden=\"true\"></i>&nbsp"
                + node.getKey()
                + "<ul>";
      } else {
        parent =
            parent
                + "<li class=\"closed\"><i class=\"fa fa-folder\" aria-hidden=\"true\"></i>&nbsp"
                + node.getKey()
                + "<ul>";
      }
      for (Pair child : (List<Pair<String, Object>>) node.getValue()) {
        parent = appendNode(parent, child, false);
      }
      parent = parent + "</ul></li>";
    }
    return parent;
  }
}
