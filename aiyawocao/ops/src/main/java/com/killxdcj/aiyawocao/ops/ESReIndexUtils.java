package com.killxdcj.aiyawocao.ops;

import com.alibaba.fastjson.JSON;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.killxdcj.aiyawocao.common.metrics.InfluxdbBackendMetrics;
import com.killxdcj.aiyawocao.common.metrics.InfluxdbBackendMetricsConfig;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.apache.commons.io.LineIterator;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ESReIndexUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(ESUtils.class);
  private static final Logger INDEX_ERROR = LoggerFactory.getLogger("indexerror");
  private static final Logger METADATA = LoggerFactory.getLogger("metadata");

  private Namespace nameSpace;
  private Timer costtime;
  private Meter successed;
  private Meter error;

  public static void main(String[] args) {
    ESReIndexUtils esReIndexUtils = new ESReIndexUtils();
    esReIndexUtils.start(args);
  }

  public void start(String[] args) {
    ArgumentParser parser = buildParser();
    try {
      nameSpace = parser.parseArgs(args);
      switch (nameSpace.getString("action")) {
        case "index":
          index();
          break;
        default:
          break;
      }
    } catch (ArgumentParserException e) {
      parser.handleError(e);
    }
  }

  private void index() {
    MetricRegistry registry =
        InfluxdbBackendMetrics.startMetricReport(new InfluxdbBackendMetricsConfig());
    costtime = registry.timer(MetricRegistry.name(ESUtils.class, "index.costtime"));
    successed =
        registry.meter(MetricRegistry.name(ESUtils.class, "index.throughput.successed"));
    error = registry.meter(MetricRegistry.name(ESUtils.class, "index.throughput.error"));

    String path = nameSpace.getString("path");
    String index = nameSpace.getString("index");
    String type = nameSpace.getString("type");
    int bulkSize = nameSpace.getInt("bulkSize");

    try (RestHighLevelClient client = buildESClient()) {
      File root = new File(path);
      if (root.isFile()) {
        indexFile(root, client, index, type, bulkSize);
      } else {
        for (File file : FileUtils.listFiles(root, null, true)) {
          indexFile(file, client, index, type, bulkSize);
        }
      }
    } catch (IOException e) {
      LOGGER.error("close es client error", e);
    } finally {
      InfluxdbBackendMetrics.shutdown();
    }
    LOGGER.info("fininshed");
  }

  private void indexFile(File file, RestHighLevelClient client, String index, String type,
      int bulkSize) {
    LOGGER.info("start index {}", file);
    try {
      LineIterator lineIterator = FileUtils.lineIterator(file);
      List<String> batch = new ArrayList<>(bulkSize);
      while (lineIterator.hasNext()) {
        batch.add(lineIterator.nextLine());
        if (batch.size() >= bulkSize) {
          indexBatch(index, type, client, batch);
          batch.clear();
        }
      }
      if (batch.size() > 0) {
        indexBatch(index, type, client, batch);
      }
    } catch (IOException e) {
      LOGGER.error("metadata file error, " + file.getAbsolutePath(), e);
    }
    LOGGER.info("complete index {}", file);
  }

  private void indexBatch(String index, String type, RestHighLevelClient client, List<String> batch) {
    long start = System.currentTimeMillis();
    try {
      BulkRequest request = new BulkRequest();
      Map<String, String> indexId2Meta = new HashMap<>();
      for (String metadata : batch) {
        try {
          Map<String, Object> metaHuman = JSON.parseObject(metadata, Map.class);
          if (metaHuman.containsKey("files")) {
            long length = 0;
            for (Map<String, String> file : (List<Map<String, String>>) metaHuman.get("files")) {
              length += Long.parseLong(file.get("length"));
            }
            metaHuman.put("length", "" + length);
            metaHuman.put("filenum", "" + ((List<Map<String, String>>) metaHuman.get("files")).size());
          } else {
            metaHuman.put("filenum", "1");
          }
          String newMetadata = JSON.toJSONString(metaHuman);
          METADATA.info(newMetadata);

          String infohash = (String)metaHuman.get("infohash");
          request.add(new IndexRequest(index, type, infohash).source(newMetadata, XContentType.JSON));
          indexId2Meta.put(infohash, newMetadata);
        } catch (Throwable t) {
          INDEX_ERROR.info("index error build request, {}", metadata);
        }
      }
      BulkResponse responses = client.bulk(request);
      for (BulkItemResponse response : responses.getItems()) {
        if (response.status().getStatus() != 201 && response.status().getStatus() != 200) {
          error.mark();
          INDEX_ERROR.info("index error status, status: {}, failuerMsg:{}, meta:{}",
              response.status().getStatus(), response.getFailureMessage(), indexId2Meta.get(response.getId()));
        } else {
          successed.mark();
        }
      }
    } catch (Throwable e) {
      for (String metadata : batch) {
        INDEX_ERROR.info("index error api, {}", metadata);
      }
      error.mark();
      LOGGER.error("index error", e);
    } finally {
      costtime.update(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
    }
  }

  private RestHighLevelClient buildESClient() {
    String[] esAddrs = nameSpace.getString("esAddr").split(":");
    return new RestHighLevelClient(
        RestClient.builder(new HttpHost(esAddrs[0], Integer.parseInt(esAddrs[1]), "http")));
  }

  private ArgumentParser buildParser() {
    ArgumentParser parser =
        ArgumentParsers.newFor("ESUtils").build().defaultHelp(true).description("ES Utils");

    Subparsers subparsers = parser.addSubparsers().title("actions");
    Subparser index =
        subparsers
            .addParser("index")
            .setDefault("action", "index")
            .defaultHelp(true)
            .help("Index Json File");
    index.addArgument("-p", "--path").required(true).help("Json Metadata File/Dir path");
    index.addArgument("-e", "--esAddr").required(true).help("ES addr, host:port");
    index.addArgument("-i", "--index").required(true).help("Index name");
    index.addArgument("-t", "--type").required(true).help("Type name");
    index
        .addArgument("-b", "--bulkSize")
        .required(false)
        .type(Integer.class)
        .setDefault(20)
        .help("Bulk Size");

    return parser;
  }
}
