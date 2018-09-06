package com.killxdcj.aiyawocao.web.service;

import com.alibaba.fastjson.JSON;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.killxdcj.aiyawocao.common.utils.TimeUtils;
import com.killxdcj.aiyawocao.web.model.Metadata;
import com.killxdcj.aiyawocao.web.model.SearchResult;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
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
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ESService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ESService.class);

  private static final String ANALYZE_BODY_FMT ="{\"field\":\"name\",\"text\":\"%s\"}";

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

  @Autowired
  private MetricsService metricsService;

  private Meter searchMeter;
  private Meter detailMeter;
  private Meter analyzeMeter;
  private Meter recentMeter;
  private Timer searchTimer;
  private Timer detailTimer;
  private Timer analyzeTimer;
  private Timer recentTimer;

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

      return new Metadata(searchHits.getAt(0));
    } finally {
      detailMeter.mark();
      detailTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
    }
  }

  public SearchResult search(String keyword, int from, int size) throws IOException {
    return search(keyword, from, size, "", false);
  }

  public SearchResult search(String keyword, int from, int size, String sortFiled, boolean fuzzyQuery) throws IOException {
    long start = TimeUtils.getCurTime();
    try {
      MultiMatchQueryBuilder queryBuilder = new MultiMatchQueryBuilder(keyword, "name", "files.path")
          .type(Type.MOST_FIELDS);
      if (!fuzzyQuery) {
        queryBuilder.operator(Operator.AND);
      }

      HighlightBuilder highlightBuilder = new HighlightBuilder()
          .field("name")
//          .field("files.path")
          .preTags("skrbt-high-pre")
          .postTags("skrbt-high-post");

      SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(queryBuilder)
              .highlighter(highlightBuilder)
              .from(from)
              .size(size)
              .timeout(new TimeValue(30, TimeUnit.SECONDS))
              .fetchSource(true);

      if (!StringUtils.isEmpty(sortFiled)) {
        switch (sortFiled) {
          case "date":
            searchSourceBuilder.sort("date", SortOrder.DESC);
            default:
              // TODO
              break;
        }
      }

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
      Response response = client
          .getLowLevelClient()
          .performRequest("GET", analyze_endpoint, Collections.EMPTY_MAP,
              new StringEntity(String.format(ANALYZE_BODY_FMT, text), ContentType.APPLICATION_JSON));
      if (response.getStatusLine().getStatusCode() == 200) {
        return JSON.parseObject(EntityUtils.toString(response.getEntity()))
            .getJSONArray("tokens")
            .stream()
            .sorted((o1, o2) -> {
              String o1type = (String) ((Map<String, Object>) o1).get("type");
              String o2type = (String) ((Map<String, Object>) o2).get("type");
              if (o1type.equals("CN_WORD") && o2type.equals("CN_WORD")) {
                return ((String) ((Map<String, Object>) o2).get("type")).length() -
                    ((String) ((Map<String, Object>) o1).get("type")).length();
              }
              if (o1type.equals("CN_WORD")) {
                return -1;
              }
              if (o2type.equals("CN_WORD")) {
                return 1;
              }
              return ((String) ((Map<String, Object>) o2).get("type")).length() -
                  ((String) ((Map<String, Object>) o1).get("type")).length();
            })
            .map(new Function<Object, String>() {
              @Override
              public String apply(Object o) {
                return (String) ((Map<String, Object>) o).get("token");
              }
            })
            .filter(s -> s.length() >= 2)
            .collect(Collectors.toList());
      }
    } catch (IOException e) {
      LOGGER.error("analyz keyword error", e);
    } finally {
      analyzeMeter.mark();
      analyzeTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
    }
    return Collections.emptyList();
  }

  public SearchResult recent(int from, int size) throws IOException {
    long start = TimeUtils.getCurTime();
    try {
      QueryBuilder queryBuilder = new MatchAllQueryBuilder();
      SearchSourceBuilder searchSourceBuilder =
          new SearchSourceBuilder()
              .query(queryBuilder)
              .sort("date", SortOrder.DESC)
              .from(from)
              .size(size)
              .timeout(new TimeValue(30, TimeUnit.SECONDS))
              .fetchSource(true);
      SearchRequest searchRequest =
          new SearchRequest("metadata").types("v1").source(searchSourceBuilder);

      SearchResponse searchResponse = client.search(searchRequest);
      return SearchResult.fromSearchResponse(searchResponse);
    } finally {
      recentMeter.mark();
      recentTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
    }
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
    recentMeter = registry.meter(MetricRegistry.name(ESService.class, "recent.throughput"));
    searchTimer = registry.timer(MetricRegistry.name(ESService.class, "search.costtime"));
    detailTimer = registry.timer(MetricRegistry.name(ESService.class, "detail.costtime"));
    analyzeTimer = registry.timer(MetricRegistry.name(ESService.class, "analyze.costtime"));
    recentTimer = registry.timer(MetricRegistry.name(ESService.class, "recent.costtime"));
  }

  @PreDestroy
  public void closeESClient() {
    try {
      client.close();
    } catch (IOException e) {
      LOGGER.error("close es client error", e);
    }
  }

  public static void main(String[] args) throws IOException {
    HttpHost esHost = new HttpHost("10.8.121.183", 9200, "http");
    RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(esHost));
    QueryBuilder queryBuilder =
        new MultiMatchQueryBuilder("建国大业", "name", "files.path")
            .type(Type.MOST_FIELDS)
            .operator(Operator.AND);

    HighlightBuilder highlightBuilder = new HighlightBuilder()
        .field("name")
        .field("files.path")
        .preTags("skrbt-high-pre")
        .postTags("skrbt-high-post");

    SearchSourceBuilder searchSourceBuilder =
        new SearchSourceBuilder()
            .query(queryBuilder)
            .highlighter(highlightBuilder)
            .from(0)
            .size(1)
            .timeout(new TimeValue(30, TimeUnit.SECONDS))
            .fetchSource(true);

    SearchRequest searchRequest =
        new SearchRequest("metadata").types("v1").source(searchSourceBuilder);

    SearchResponse searchResponse = client.search(searchRequest);
    for (SearchHit hit : searchResponse.getHits()) {
      System.out.println(hit.getHighlightFields());
    }
    SearchResult searchResult = new SearchResult(searchResponse);
    for (Metadata me : searchResult.getMetadatas()) {
      System.out.println(me.getName() + " -> " + me.getHighlightName());
      System.out.println(me.getDigestFiles(10));
    }
  }
}
