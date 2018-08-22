package com.killxdcj.aiyawocao.web.service;

import com.alibaba.fastjson.JSON;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.killxdcj.aiyawocao.common.utils.TimeUtils;
import com.killxdcj.aiyawocao.web.model.Metadata;
import com.killxdcj.aiyawocao.web.model.SearchResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Response;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ESService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ESService.class);

  private static final String ANALYZE_BODY_FMT =
      "{\n" + "  \"field\": \"name\", \n" + "  \"text\":  \"%s\"\n" + "}";

  @Value("${es.host}")
  private String host;

  @Value("${es.port}")
  private int port;

  @Value("${es.index}")
  private String index;

  @Value("${es.type}")
  private String type;

  private RestHighLevelClient client;

  private String analyze_endpoint;

  @Autowired private MetricsService metricsService;

  private Meter searchMeter;
  private Meter detailMeter;
  private Meter analyzeMeter;
  private Timer searchTimer;
  private Timer detailTimer;
  private Timer analyzeTimer;

  public Object searchx(String keyword, int from, int size) throws IOException {
    QueryBuilder queryBuilder =
        new MultiMatchQueryBuilder(keyword, "name", "files.path").type(Type.MOST_FIELDS);

    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(queryBuilder)
            .from(from)
            .size(size)
            .timeout(new TimeValue(30, TimeUnit.SECONDS))
            .fetchSource(true);

    SearchRequest searchRequest =
        new SearchRequest("metadata").types("v1").source(searchSourceBuilder);

    SearchResponse searchResponse = client.search(searchRequest);
    return searchResponse.getHits();
  }

  public Metadata detail(String infohash) throws IOException {
    long start = TimeUtils.getCurTime();
    try {
      QueryBuilder queryBuilder = new TermQueryBuilder("infohash", infohash.toUpperCase());

      SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(queryBuilder)
              .timeout(new TimeValue(30, TimeUnit.SECONDS));

      SearchRequest searchRequest =
          new SearchRequest(index).types(type).source(searchSourceBuilder);

      SearchResponse searchResponse = client.search(searchRequest);
      SearchHits searchHits = searchResponse.getHits();
      if (searchHits.totalHits == 0) {
        return null;
      }

      if (searchHits.totalHits > 1) {
        LOGGER.warn("find duplica infohash {}", infohash);
      }

      return new Metadata(searchHits.getAt(0).getSourceAsMap());
    } finally {
      detailMeter.mark();
      detailTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
    }
  }

  public SearchResult search(String keyword, int from, int size) throws IOException {
    long start = TimeUtils.getCurTime();
    try {
      QueryBuilder queryBuilder =
          new MultiMatchQueryBuilder(keyword, "name", "files.path")
              .type(Type.MOST_FIELDS)
              .operator(Operator.AND);

      SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(queryBuilder)
              .from(from)
              .size(size)
              .timeout(new TimeValue(30, TimeUnit.SECONDS))
              .fetchSource(true);

      SearchRequest searchRequest =
          new SearchRequest("metadata").types("v1").source(searchSourceBuilder);

      SearchResponse searchResponse = client.search(searchRequest);
      return SearchResult.fromSearchResponse(searchResponse);
    } finally {
      searchMeter.mark();
      searchTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
    }
  }

  public List<String> analyze(String text) {
    long start = TimeUtils.getCurTime();
    try {
      Response response =
          client
              .getLowLevelClient()
              .performRequest(
                  "GET",
                  analyze_endpoint,
                  Collections.EMPTY_MAP,
                  new StringEntity(
                      String.format(ANALYZE_BODY_FMT, text), ContentType.APPLICATION_JSON));
      if (response.getStatusLine().getStatusCode() == 200) {
        List<String> keywords = new ArrayList<>();
        JSON.parseObject(EntityUtils.toString(response.getEntity()))
            .getJSONArray("tokens")
            .forEach(
                token -> {
                  String keyword = (String) ((Map<String, Object>) token).get("token");
                  if (keyword.length() >= 2 && keyword.length() <= 8) {
                    keywords.add(keyword);
                  }
                });
        Collections.sort(keywords, (o1, o2) -> o2.length() - o1.length());
        return keywords;
      }
    } catch (IOException e) {
      LOGGER.error("analyz keyword error", e);
    } finally {
      analyzeMeter.mark();
      analyzeTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
    }
    return Collections.emptyList();
  }

  @PostConstruct
  public void initESClient() {
    HttpHost esHost = new HttpHost(host, port, "http");
    client = new RestHighLevelClient(RestClient.builder(esHost));
    analyze_endpoint = "/" + index + "/_analyze";
    LOGGER.info("init es client with {}:{}", host, port);

    MetricRegistry registry = metricsService.registry();
    searchMeter = registry.meter(MetricRegistry.name(ESService.class, "search.throughput"));
    detailMeter = registry.meter(MetricRegistry.name(ESService.class, "detail.throughput"));
    analyzeMeter = registry.meter(MetricRegistry.name(ESService.class, "analyze.throughput"));
    searchTimer = registry.timer(MetricRegistry.name(ESService.class, "search.costtime"));
    detailTimer = registry.timer(MetricRegistry.name(ESService.class, "detail.costtime"));
    analyzeTimer = registry.timer(MetricRegistry.name(ESService.class, "analyze.costtime"));
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
