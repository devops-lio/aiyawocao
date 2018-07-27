package com.killxdcj.aiyawocao.ops;

import com.alibaba.fastjson.JSON;
import com.aliyun.oss.OSSClient;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.exception.InvalidBittorrentPacketException;
import com.killxdcj.aiyawocao.common.metrics.InfluxdbBackendMetrics;
import com.killxdcj.aiyawocao.common.metrics.InfluxdbBackendMetricsConfig;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang3.StringUtils;

public class OSSOps {
  private Namespace namespace;
  private OSSClient ossClient;
  private String bucketName;

  public static void main(String[] args) {
    OSSOps ossOps = new OSSOps();
    ossOps.start(args);
  }

  public void start(String[] args) {
    ArgumentParser parser = buildParser();
    try {
      namespace = parser.parseArgs(args);
      ossClient = buildOSSClient(namespace);
      bucketName = namespace.getString("bucketName");
      switch (namespace.getString("action")) {
        case "download":
          downloadFile();
          break;
        case "archive":
          archive();
        default:
          break;
      }

    } catch (ArgumentParserException e) {
      parser.handleError(e);
    } finally {
      if (ossClient != null) {
        ossClient.shutdown();
      }
    }
  }

  private void downloadFile() {
    String localFile = namespace.getString("localFile");
    String remoteFile = namespace.getString("remoteFile");
    if (StringUtils.isEmpty(localFile)) {
      int idx = remoteFile.lastIndexOf("/");
      if (idx != -1) {
        localFile = remoteFile.substring(idx + 1);
      } else {
        localFile = remoteFile;
      }
    }

    if (!ossClient.doesObjectExist(bucketName, remoteFile)) {
      System.out.println("RemoteFile does not exist, " + remoteFile);
      return;
    }

    try (InputStream in = ossClient.getObject(bucketName, remoteFile).getObjectContent()) {
      FileUtils.copyInputStreamToFile(in, new File(localFile));
    } catch (IOException e) {
      System.out.println("Download file error, " + remoteFile);
      e.printStackTrace();
    }
  }

  private void archive() {
    MetricRegistry registry = InfluxdbBackendMetrics.startMetricReport(new InfluxdbBackendMetricsConfig());
    String localIndex = namespace.getString("localIndex");
    int maxLine = namespace.getInt("maxLine");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    String fileFormat = namespace.getString("fileFormat");
    String collectionTime = sdf.format(new Date());

    int batchSize = (maxLine > 0 && maxLine < 100) ? maxLine : 100;
    List<String> metadataBatch = new ArrayList<>(batchSize);
    int curArchivedIdx = 0;
    int cnt = 0;

    Meter successed = registry.meter(MetricRegistry.name(OSSOps.class, "archive.successed"));
    Meter decodeFailed = registry.meter(MetricRegistry.name(OSSOps.class, "archive.failed.decode"));
    Meter otherFailed = registry.meter(MetricRegistry.name(OSSOps.class, "archive.failed.other"));
    Timer costtime = registry.timer(MetricRegistry.name(OSSOps.class, "archive.fetch"));

    long start;
    try (LineIterator lineIterator = FileUtils.lineIterator(new File(localIndex))) {
      while (lineIterator.hasNext()) {
        start = System.currentTimeMillis();
        String infohash = lineIterator.nextLine();
        String bucketKey = buildBucketKey(infohash);
        if (!ossClient.doesObjectExist(bucketName, bucketKey)) {
          System.out.println("infohash dose not exist, " + infohash);
          continue;
        }

        try (InputStream in = ossClient.getObject(bucketName, bucketKey).getObjectContent()) {
          Bencoding bencoding = new Bencoding(IOUtils.toByteArray(in));
          Map<String, Object> metaHuman = (Map<String, Object>) bencoding.decode().toHuman();
          metaHuman.put("infohash", infohash.toUpperCase());
          metaHuman.put("date", collectionTime);
          metadataBatch.add(JSON.toJSONString(metaHuman));
          successed.mark();
        } catch (InvalidBittorrentPacketException e) {
          decodeFailed.mark();
          System.out.println("decode metadata error, " + infohash);
          e.printStackTrace();
        } catch (Exception e) {
          otherFailed.mark();
          System.out.println("archived metadata error, " + infohash);
          e.printStackTrace();
        }

        if (metadataBatch.size() >=  batchSize) {
          String archivedFile = String.format(fileFormat, curArchivedIdx);
          FileUtils.writeLines(new File(archivedFile), metadataBatch, true);
          cnt += metadataBatch.size();
          metadataBatch.clear();
          collectionTime = sdf.format(new Date());
          if (maxLine != -1 && cnt >= maxLine) {
            curArchivedIdx++;
            cnt = 0;
          }
        }
        costtime.update(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
      }

      String archivedFile = String.format(fileFormat, curArchivedIdx);
      FileUtils.writeLines(new File(archivedFile), metadataBatch, true);
    } catch (IOException e) {
      e.printStackTrace();
    } finally{
      InfluxdbBackendMetrics.shutdown();
    }
    System.out.println("finished");
  }

  private String buildBucketKey(String infohash) {
    infohash = infohash.toUpperCase();
    return ""
        + infohash.charAt(0)
        + infohash.charAt(1)
        + "/"
        + infohash.charAt(2)
        + infohash.charAt(3)
        + "/"
        + infohash.charAt(4)
        + infohash.charAt(5)
        + "/"
        + infohash;
  }

  private OSSClient buildOSSClient(Namespace namespace) {
    return new OSSClient(
        namespace.getString("endpoint"),
        namespace.getString("accessKeyId"),
        namespace.getString("accessKeySecret"));
  }

  private ArgumentParser buildParser() {
    ArgumentParser parser = ArgumentParsers.newFor("OSSUtils")
        .build()
        .defaultHelp(true)
        .description("Download file from OSS");

    Subparsers subparsers = parser.addSubparsers().title("actions");

    Subparser download = subparsers.addParser("download")
        .setDefault("action", "download")
        .defaultHelp(true)
        .help("Download File");
    addOSSArguments(download);
    download.addArgument("-r", "--remoteFile").required(true).help("Remote File");
    download.addArgument("-l", "--localFile").required(false).help("Local File");

    Subparser archive = subparsers.addParser("archive")
        .setDefault("action", "archive")
        .defaultHelp(true)
        .help("Archive Metadata to file");
    addOSSArguments(archive);
    archive.addArgument("-l", "--localIndex").required(true).help("Local IndexFile");
    archive.addArgument("-m", "--maxLine").required(false).type(Integer.class).setDefault(-1).help("Max Line PerFile");
    archive.addArgument("-f", "--fileFormat").required(false).setDefault(-1).help("Archived File Format");

    return parser;
  }

  private void addOSSArguments(Subparser subparser) {
    subparser.addArgument("-e", "--endpoint").required(true).help("OSS Endpoint");
    subparser.addArgument("-i", "--accessKeyId").required(true).help("OSS AccessKeyId");
    subparser.addArgument("-s", "--accessKeySecret").required(true).help("OSS AccessKeySecret");
    subparser.addArgument("-b", "--bucketName").required(true).help("OSS BucketName");
  }
}
