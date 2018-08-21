package com.killxdcj.aiyawocao.ops;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.CompactionStyle;
import org.rocksdb.CompressionType;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Statistics;
import org.rocksdb.util.SizeUnit;

public class IndexUtils {

  private Namespace namespace;

  public static byte[] toHex(String xx) throws DecoderException {
    return Hex.decodeHex(xx.toCharArray());
  }

  public static String toString(byte[] xx) {
    return Hex.encodeHexString(xx);
  }

  public static void main(String[] args) {
    IndexUtils indexUtils = new IndexUtils();
    indexUtils.start(args);
  }

  public void start(String[] args) {
    ArgumentParser parser = buildParser();
    try {
      namespace = parser.parseArgs(args);
      switch (namespace.getString("action")) {
        case "file2rdb":
          transIndexFile2RocksDB();
          break;
        case "rdb2file":
          transRocksDB2IndexFile();
          break;
        default:
          break;
      }
    } catch (ArgumentParserException e) {
      parser.handleError(e);
    }
    System.out.println("finished");
  }

  private void transIndexFile2RocksDB() {
    String indexFilePath = namespace.getString("indexPath");
    String rocksdbPath = namespace.getString("rocksDBPath");

    File indexFile = new File(indexFilePath);
    try (RocksDB rocksDB = buildRDB(rocksdbPath)) {
      if (indexFile.isFile()) {
        transIndexFile2RocksDB(indexFile, rocksDB);
      } else {
        for (File file : indexFile.listFiles()) {
          transIndexFile2RocksDB(file, rocksDB);
        }
      }
    } catch (RocksDBException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void transIndexFile2RocksDB(File file, RocksDB rocksDB) throws IOException {
    LineIterator lineIterator = FileUtils.lineIterator(file);
    byte[] dumpy = new byte[0];
    while (lineIterator.hasNext()) {
      String infohash = lineIterator.nextLine();
      try {
        rocksDB.put(toHex(infohash), dumpy);
      } catch (Exception e) {
        System.out.println("indexFile to rocksDB error, " + infohash);
        e.printStackTrace();
      }
    }
  }

  private void transRocksDB2IndexFile() {
    String indexFilePath = namespace.getString("indexPath");
    String rocksdbPath = namespace.getString("rocksDBPath");
    try (RocksDB db = buildRDB(rocksdbPath)) {
      try (BufferedWriter writer = new BufferedWriter(
          new OutputStreamWriter(new FileOutputStream(new File(indexFilePath))))) {
        try (final RocksIterator iterator = db.newIterator()) {
          for (iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
            writer.write(toString(iterator.key()).toUpperCase() + "\n");
          }
        }
      } catch (Exception e) {
        System.out.println("rocksDB to indexFile error");
        e.printStackTrace();
      }
    } catch (RocksDBException e) {
      e.printStackTrace();
    }
  }

  private RocksDB buildRDB(String dbPath) throws RocksDBException {
    Options options = new Options();
    options.setCreateIfMissing(true)
        .setStatistics(new Statistics())
        .setWriteBufferSize(8 * SizeUnit.KB)
        .setMaxWriteBufferNumber(3)
        .setMaxBackgroundCompactions(10)
        .setCompressionType(CompressionType.SNAPPY_COMPRESSION)
        .setCompactionStyle(CompactionStyle.UNIVERSAL);

    final BlockBasedTableConfig table_options = new BlockBasedTableConfig();
    table_options.setBlockCacheSize(64 * SizeUnit.KB)
        .setFilter(new BloomFilter(10))
        .setCacheNumShardBits(6)
        .setBlockSizeDeviation(5)
        .setBlockRestartInterval(10)
        .setCacheIndexAndFilterBlocks(true)
        .setHashIndexAllowCollision(false)
        .setBlockCacheCompressedSize(64 * SizeUnit.KB)
        .setBlockCacheCompressedNumShardBits(10);
    options.setTableFormatConfig(table_options);

    return RocksDB.open(options, dbPath);
  }

  private ArgumentParser buildParser() {
    ArgumentParser parser = ArgumentParsers.newFor("IndexUtils")
        .build()
        .defaultHelp(true)
        .description("Index Manager Utils");

    Subparsers subparsers = parser.addSubparsers().title("actions");
    Subparser file2rdb = subparsers.addParser("file2rdb")
        .setDefault("action", "file2rdb")
        .defaultHelp(true)
        .help("Trans IndexFile to RocksDB");
    file2rdb.addArgument("-f", "--indexPath").required(true).help("IndexFile Path");
    file2rdb.addArgument("-r", "--rocksDBPath").required(true).help("RocksDB Dir");

    Subparser rdb2file = subparsers.addParser("rdb2file")
        .setDefault("action", "rdb2file")
        .defaultHelp(true)
        .help("Trans RocksDB to IndexFile");
    rdb2file.addArgument("-f", "--indexPath").required(true).help("IndexFile Path");
    rdb2file.addArgument("-r", "--rocksDBPath").required(true).help("RocksDB Path");

    return parser;
  }
}
