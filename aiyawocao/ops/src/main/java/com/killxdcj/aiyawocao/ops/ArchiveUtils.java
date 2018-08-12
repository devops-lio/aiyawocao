package com.killxdcj.aiyawocao.ops;

import com.alibaba.fastjson.JSON;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiveUtils {

  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveUtils.class);
  private static final Logger METADATA = LoggerFactory.getLogger("metadata");
  private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private Namespace nameSpace;
  private int transed = 0;
  private int failed = 0;

  public static void main(String[] args) {
    ArchiveUtils archiveUtils = new ArchiveUtils();
    archiveUtils.start(args);
  }

  public void start(String[] args) {
    ArgumentParser parser = buildParser();
    try {
      nameSpace = parser.parseArgs(args);
      switch (nameSpace.getString("action")) {
        case "2human":
          original2human();
          break;
        default:
          break;
      }
    } catch (ArgumentParserException e) {
      parser.handleError(e);
    }
  }

  private void original2human() {
    String path = nameSpace.getString("path");
    File root = new File(path);
    if (root.isFile()) {
      transFile(root);
    } else {
      transDir(new File(path));
    }
  }

  private void transDir(File file) {
    if (file.isDirectory()) {
      LOGGER.info("dir trans start {}", file.getAbsolutePath());
      IOFileFilter fileFilter = new IOFileFilter() {
        @Override
        public boolean accept(File file) {
          return true;
        }

        @Override
        public boolean accept(File dir, String name) {
          return true;
        }
      };
      for (File tmp : FileUtils.listFilesAndDirs(file, fileFilter, null)) {
        if (tmp.isFile()) {
          transFile(tmp);
        } else {
          transDir(tmp);
        }
      }
      LOGGER.info("dir trans finished {}", file.getAbsolutePath());
    } else {
      transFile(file);
    }
  }

  private void transFile(File file) {
    try {
      Bencoding bencoding = new Bencoding(FileUtils.readFileToByteArray(file));
      Map<String, Object> metaHuman = (Map<String, Object>) bencoding.decode().toHuman();
      metaHuman.put("infohash", file.getName().toUpperCase());
      metaHuman.put("date", SDF.format(new Date()));
      METADATA.info(JSON.toJSONString(metaHuman));
      transed++;
    } catch (Exception e) {
      failed++;
      LOGGER.error("trans error, " + file.getPath(), e);
    }
  }

  private ArgumentParser buildParser() {
    ArgumentParser parser = ArgumentParsers.newFor("ArchiveUtils")
        .build()
        .defaultHelp(true)
        .description("Archive Utils");

    Subparsers subparsers = parser.addSubparsers().title("action");
    Subparser original2human = subparsers.addParser("2human")
        .setDefault("action", "2human")
        .defaultHelp(true)
        .help("Trans original metadata to human");
    original2human.addArgument("-p", "--path").required(true)
        .help("Dir contains original metadata");

    return parser;
  }
}
