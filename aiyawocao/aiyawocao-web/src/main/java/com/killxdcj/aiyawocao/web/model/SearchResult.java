package com.killxdcj.aiyawocao.web.model;

import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;

public class SearchResult {

  private SearchResponse searchResponse;

  public SearchResult(SearchResponse searchResponse) {
    this.searchResponse = searchResponse;
  }

  public static SearchResult fromSearchResponse(SearchResponse searchResponse) {
    return new SearchResult(searchResponse);
  }

  public TimeValue getTook() {
    return searchResponse.getTook();
  }

  public long getTotalHits() {
    return searchResponse.getHits().getTotalHits();
  }

  public List<Metadata> getMetadatas() {
    List<Metadata> metadatas = new ArrayList<>();
    for (SearchHit searchHit : searchResponse.getHits()) {
      metadatas.add(new Metadata(searchHit.getSourceAsMap()));
    }
    return metadatas;
  }
}
