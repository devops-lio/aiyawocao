package com.killxdcj.aiyawocao.web.model;

import com.killxdcj.aiyawocao.common.utils.CommonUtils;
import com.killxdcj.aiyawocao.web.utils.WebUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javafx.util.Pair;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;

public class Metadata {

  private static final String HIGHLIGHT_PRE = "<span class=\"highlight\">";
  private static final String HIGHLIGHT_POST = "</span>";
  private static int DEFAULT_DIGEST_FILES_SIZE = 10;

  private Map<String, Object> originalData;
  private Map<String, HighlightField> highlightFields;
  private SearchHit searchHit;

  public Metadata(SearchHit searchHit) {
    this.searchHit = searchHit;
    this.originalData = searchHit.getSourceAsMap();
    this.highlightFields = searchHit.getHighlightFields();
  }

  public Map<String, Object> getOriginalData() {
    return originalData;
  }

  public String getName() {
    return (String) originalData.get("name");
  }

  public String getHighlightName() {
    HighlightField highlightField = highlightFields.get("name");
    if (highlightField == null || highlightField.getFragments().length == 0) {
      return getName();
    }
    return replaceHighlight(highlightField.getFragments()[0].string());
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
      for (Map<String, String> file : (List<Map<String, String>>) originalData.get("files")) {
        totalSize += Long.parseLong(file.get("length"));
      }
    } else {
      totalSize += Long.parseLong((String) originalData.get("length"));
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

  public List<String> getAllOriginalFiles() {
    if (originalData.containsKey("files")) {
      return ((List<Map<String, String>>)originalData.get("files")).stream()
          .map(o -> {
            String[] tmps = o.get("path").split("/");
            return tmps[tmps.length - 1];
          })
          .filter(s -> s.indexOf("请升级到BitComet") == -1)
          .collect(Collectors.toList());
    } else {
      return Arrays.asList(getName());
    }
  }

  public List<Pair<String, Long>> getDigestFiles() {
    return getDigestFiles(DEFAULT_DIGEST_FILES_SIZE);
  }

  public List<Pair<String, Long>> getDigestFiles(int digestFilesSize) {
    List<Pair<String, Long>> rets = new ArrayList<>();

    Map<String, Long> fileMap = new HashMap<>();
    if (originalData.containsKey("files")) {
      List<Map<String, String>> files = (List<Map<String, String>>) originalData.get("files");
      for (Map<String, String> file : files) {
        if (file.get("path").indexOf("请升级到BitComet") != -1) {
          continue;
        }
        String[] tmps = file.get("path").split("/");
        fileMap.put(tmps[tmps.length - 1], Long.parseLong(file.get("length")));
      }
    } else {
      fileMap.put((String)originalData.get("name"), Long.parseLong((String) originalData.get("length")));
    }

    HighlightField highlightField = highlightFields.get("files.path");
    if (highlightField != null && highlightField.getFragments().length != 0) {
      for (Text text : highlightField.getFragments()) {
        String[] paths =text.string().split("/");
        String name = paths[paths.length - 1];
        String originalName = name.replace("skrbt-high-pre", "")
            .replace("skrbt-high-post", "");
        if (fileMap.containsKey(originalName)) {
          rets.add(new Pair(attachFileIcon(replaceHighlight(name)), CommonUtils.fileSize2Human(fileMap.get(originalName))));
          fileMap.remove(originalName);
        }
      }
    }

    for (Entry<String, Long> entry : fileMap.entrySet()){
      if (rets.size() >= digestFilesSize) {
        break;
      }
      rets.add(new Pair(attachFileIcon(entry.getKey()), CommonUtils.fileSize2Human(entry.getValue())));
    }

    return rets;
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
              + "<li>"
              + WebUtils.calcAwesomeIcon(node.getKey()) + "&nbsp;"
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

  private String replaceHighlight(String text) {
    return text.replace("skrbt-high-pre", HIGHLIGHT_PRE)
        .replace("skrbt-high-post", HIGHLIGHT_POST);
  }

  private String attachFileIcon(String fileName) {
    return WebUtils.calcAwesomeIcon(fileName) + "&nbsp" + fileName;
  }
}
