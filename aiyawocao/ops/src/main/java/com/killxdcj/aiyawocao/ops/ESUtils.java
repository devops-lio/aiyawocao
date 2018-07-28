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
import java.util.List;
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

public class ESUtils {
  private Namespace nameSpace;

  public static void main(String[] args) {
    ESUtils esUtils = new ESUtils();
    esUtils.start(args);
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
    Timer costtime = registry.timer(MetricRegistry.name(ESUtils.class, "index.costtime"));
    Meter successed =
        registry.meter(MetricRegistry.name(ESUtils.class, "index.throughput.successed"));
    Meter error = registry.meter(MetricRegistry.name(ESUtils.class, "index.throughput.error"));

    String metadataFile = nameSpace.getString("metadataFile");
    String index = nameSpace.getString("index");
    String type = nameSpace.getString("type");
    int bulkSize = nameSpace.getInt("bulkSize");

    long start;
    try (RestHighLevelClient client = buildESClient()) {
      LineIterator lineIterator = FileUtils.lineIterator(new File(metadataFile));
      List<String> batch = new ArrayList<>(bulkSize);
      while (lineIterator.hasNext()) {
        batch.add(lineIterator.nextLine());
        if (batch.size() >= bulkSize) {
          start = System.currentTimeMillis();
          try {
            BulkRequest request = new BulkRequest();
            for (String metadata : batch) {
              String infohash = JSON.parseObject(metadata).getString("infohash");
              request.add(
                  new IndexRequest(index, type, infohash).source(metadata, XContentType.JSON));
            }
            BulkResponse responses = client.bulk(request);
            for (BulkItemResponse response : responses.getItems()) {
              if (response.status().getStatus() != 201 && response.status().getStatus() != 200) {
                error.mark();
                System.out.println(
                    "index error, "
                        + response.getId()
                        + " -> "
                        + response.status().getStatus()
                        + ", "
                        + response.getFailureMessage());
              } else {
                successed.mark();
              }
            }
          } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            for (String metadata : batch) {
              sb.append("," + JSON.parseObject(metadata).getString("infohash"));
            }
            System.out.println("index error" + sb.toString());
            e.printStackTrace();
          } finally {
            batch.clear();
            costtime.update(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      InfluxdbBackendMetrics.shutdown();
    }
    System.out.println("fininshed");
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
    index.addArgument("-f", "--metadataFile").required(true).help("Json Metadata File");
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
