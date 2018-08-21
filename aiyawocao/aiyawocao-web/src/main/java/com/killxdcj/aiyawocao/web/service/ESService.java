package com.killxdcj.aiyawocao.web.service;

import com.killxdcj.aiyawocao.web.model.Metadata;
import com.killxdcj.aiyawocao.web.model.SearchResult;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ESService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ESService.class);

  @Value("${es.host}")
  private String host;

  @Value("${es.port}")
  private int port;

  @Value("${es.index}")
  private String index;

  @Value("${es.type}")
  private String type;

  private RestHighLevelClient client;

  public Object searchx(String keyword, int from, int size) throws IOException {
    QueryBuilder queryBuilder = new MultiMatchQueryBuilder(keyword, "name", "files.path")
        .type(Type.MOST_FIELDS);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .query(queryBuilder)
        .from(from)
        .size(size)
        .timeout(new TimeValue(30, TimeUnit.SECONDS))
        .fetchSource(true);

    SearchRequest searchRequest = new SearchRequest("metadata")
        .types("v1")
        .source(searchSourceBuilder);

    SearchResponse searchResponse = client.search(searchRequest);
    return searchResponse.getHits();
  }

  public Metadata detail(String infohash) throws IOException {
    QueryBuilder queryBuilder = new TermQueryBuilder("infohash", infohash.toUpperCase());

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .query(queryBuilder)
        .timeout(new TimeValue(30, TimeUnit.SECONDS));

    SearchRequest searchRequest = new SearchRequest(index)
        .types(type)
        .source(searchSourceBuilder);

    SearchResponse searchResponse = client.search(searchRequest);
    SearchHits searchHits = searchResponse.getHits();
    if (searchHits.totalHits == 0) {
      return null;
    }

    if (searchHits.totalHits > 1) {
      LOGGER.warn("find duplica infohash {}", infohash);
    }

    return new Metadata(searchHits.getAt(0).getSourceAsMap());
  }

  public SearchResult search(String keyword, int from, int size) throws IOException {
    QueryBuilder queryBuilder = new MultiMatchQueryBuilder(keyword, "name", "files.path")
        .type(Type.MOST_FIELDS)
        .operator(Operator.AND);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .query(queryBuilder)
        .from(from)
        .size(size)
        .timeout(new TimeValue(30, TimeUnit.SECONDS))
        .fetchSource(true);

    SearchRequest searchRequest = new SearchRequest("metadata")
        .types("v1")
        .source(searchSourceBuilder);

    SearchResponse searchResponse = client.search(searchRequest);
    return SearchResult.fromSearchResponse(searchResponse);
  }

  @PostConstruct
  public void initESClient() {
    HttpHost esHost = new HttpHost(host, port, "http");
    client = new RestHighLevelClient(RestClient.builder(esHost));
    LOGGER.info("init es client with {}:{}", host, port);
  }

  @PreDestroy
  public void closeESClient() {
    try {
      client.close();
    } catch (IOException e) {
      LOGGER.error("close es client error", e);
    }
  }
}
