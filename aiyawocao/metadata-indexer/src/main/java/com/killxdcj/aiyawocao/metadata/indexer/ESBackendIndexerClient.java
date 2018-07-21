package com.killxdcj.aiyawocao.metadata.indexer;

import com.alibaba.fastjson.JSON;
import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.exception.InvalidBittorrentPacketException;
import com.killxdcj.aiyawocao.metadata.indexer.config.ESIndexerConfig;
import java.io.Closeable;
import java.io.IOException;
import java.util.Map;
import org.apache.http.HttpHost;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ESBackendIndexerClient implements Closeable {
  private static final Logger LOGGER = LoggerFactory.getLogger(ESBackendIndexerClient.class);

  ESIndexerConfig config;
  RestHighLevelClient client;

  public ESBackendIndexerClient(ESIndexerConfig config) {
    this.config = config;
    client = new RestHighLevelClient(RestClient.builder(new HttpHost(config.getHostname(), config.getPort(), "http")));
  }

  @Override
  public void close() throws IOException {
    try {
      client.close();
    } catch (IOException e) {
      LOGGER.error("shutdown ESBackendIndexerClient error", e);
    }
  }

  public void index(String metadataJson) throws IOException {
    String infohash = JSON.parseObject(metadataJson).getString("infohash");
    index(infohash, metadataJson);
  }

  public void index(byte[] infohashBytes, byte[] metadataBytes) throws InvalidBittorrentPacketException, IOException {
    String infohash = new BencodedString(infohashBytes).asHexString().toUpperCase();
    Bencoding bencoding = new Bencoding(metadataBytes);
    Map<String, Object> metaHuman = (Map<String, Object>)bencoding.decode().toHuman();
    metaHuman.put("infohash", infohash);
    metaHuman.put("collection-ts", System.currentTimeMillis());
    String metadataJson = JSON.toJSONString(bencoding.decode().toHuman());
    index(infohash, metadataJson);
  }

  public void index(String infohash, String metadataJson) throws IOException {
    IndexRequest indexRequest = new IndexRequest(config.getIndex(), "doc", infohash);
    indexRequest.source(metadataJson, XContentType.JSON);
    indexRequest.timeout(TimeValue.timeValueSeconds(config.getTimeoutS()));
    IndexResponse response = client.index(indexRequest);
  }
}
