package com.killxdcj.aiyawocao.ops;

import com.alibaba.fastjson.JSON;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OSSUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(OSSUtils.class);

  private Namespace namespace;
  private OSSClient ossClient;
  private String bucketName;

  public static void main(String[] args) {
    OSSUtils ossUtils = new OSSUtils();
    ossUtils.start(args);
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
          break;
        case "list":
          list();
          break;
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
      localFile = new File(remoteFile).getName();
    }

    if (!ossClient.doesObjectExist(bucketName, remoteFile)) {
      LOGGER.info("RemoteFile does not exist, {}", remoteFile);
      return;
    }

    try (InputStream in = ossClient.getObject(bucketName, remoteFile).getObjectContent()) {
      FileUtils.copyInputStreamToFile(in, new File(localFile));
    } catch (IOException e) {
      LOGGER.error("Download file error, " + remoteFile, e);
    }
  }

  private void archive() {
    MetricRegistry registry = InfluxdbBackendMetrics.startMetricReport(new InfluxdbBackendMetricsConfig());
    String localIndex = namespace.getString("localIndex");
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    String collectionTime = sdf.format(new Date());
    Logger METADATA = LoggerFactory.getLogger("metadata");

    Meter successed = registry.meter(MetricRegistry.name(OSSUtils.class, "archive.successed"));
    Meter decodeFailed = registry.meter(MetricRegistry.name(OSSUtils.class, "archive.failed.decode"));
    Meter otherFailed = registry.meter(MetricRegistry.name(OSSUtils.class, "archive.failed.other"));
    Timer costtime = registry.timer(MetricRegistry.name(OSSUtils.class, "archive.fetch"));

    long start;
    try (LineIterator lineIterator = FileUtils.lineIterator(new File(localIndex))) {
      while (lineIterator.hasNext()) {
        start = System.currentTimeMillis();
        String infohash = lineIterator.nextLine();
        String bucketKey = buildBucketKey(infohash);
        if (!ossClient.doesObjectExist(bucketName, bucketKey)) {
          LOGGER.info("infohash dose not exist, {}", infohash);
          continue;
        }

        try (InputStream in = ossClient.getObject(bucketName, bucketKey).getObjectContent()) {
          Bencoding bencoding = new Bencoding(IOUtils.toByteArray(in));
          Map<String, Object> metaHuman = (Map<String, Object>) bencoding.decode().toHuman();
          metaHuman.put("infohash", infohash.toUpperCase());
          metaHuman.put("date", collectionTime);
          METADATA.info(JSON.toJSONString(metaHuman));
          successed.mark();
        } catch (InvalidBittorrentPacketException e) {
          decodeFailed.mark();
          LOGGER.error("decode metadata error, " + infohash, e);
        } catch (Exception e) {
          otherFailed.mark();
          LOGGER.error("archived metadata error, " + infohash, e);
        }
        costtime.update(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally{
      InfluxdbBackendMetrics.shutdown();
    }
    LOGGER.info("finished");
  }

  private void list() {
    String prefix = namespace.getString("prefix");
    Integer maxFiles = namespace.getInt("maxFiles");

    ListObjectsRequest request = new ListObjectsRequest(namespace.getString("bucketName"))
        .withPrefix(prefix)
        .withMaxKeys(maxFiles);
    for (OSSObjectSummary summary : ossClient.listObjects(request).getObjectSummaries()) {
      System.out.println(summary.getKey());
    }
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
        .description("OSS Utils");

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

    Subparser list = subparsers.addParser("list")
        .setDefault("action", "list")
        .defaultHelp(true)
        .help("List dir");
    addOSSArguments(list);
    list.addArgument("-m", "--maxFiles").required(false).type(Integer.class).setDefault(100).help("Max Files");
    list.addArgument("-p", "--prefix").required(true).help("prefix");

    return parser;
  }

  private void addOSSArguments(Subparser subparser) {
    subparser.addArgument("-e", "--endpoint").required(true).help("OSS Endpoint");
    subparser.addArgument("-i", "--accessKeyId").required(true).help("OSS AccessKeyId");
    subparser.addArgument("-s", "--accessKeySecret").required(true).help("OSS AccessKeySecret");
    subparser.addArgument("-b", "--bucketName").required(true).help("OSS BucketName");
  }
}
