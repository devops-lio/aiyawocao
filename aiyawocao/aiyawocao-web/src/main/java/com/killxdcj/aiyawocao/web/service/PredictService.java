package com.killxdcj.aiyawocao.web.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.killxdcj.aiyawocao.common.utils.TimeUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javafx.util.Pair;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Service;

@Service
public class PredictService {

  private LoadingCache<String, AtomicLong> wordsFrequency;
  private List<String> hotWordsCache = new ArrayList<>();
  private long nextUpdateTime = 0;
  private long hotWordsCacheExpiredTime = 10 * 60 * 1000;

  private static final Set<String> blkHotWords = new HashSet(){{
    add("强奸");
    add("强暴");
    add("轮奸");
  }};

  public List<String> getHotWordsCache() {
    return getHotWords(8);
  }

  public List<String> getHotWords(int size) {
    if (hotWordsCache.size() < size || TimeUtils.getCurTime() > nextUpdateTime) {
      List<String> newHotWords = wordsFrequency.asMap().keySet().stream()
          .sorted((o1, o2) -> (int) (wordsFrequency.getUnchecked(o2).get() - wordsFrequency.getUnchecked(o1).get()))
          .filter(s -> !blkHotWords.contains(s) && s.length() > 1)
          .collect(Collectors.toList());
      if (newHotWords.size() > size) {
        newHotWords = newHotWords.subList(0, size);
      }
      if (newHotWords.size() != 0) {
        hotWordsCache = newHotWords;
      }
      nextUpdateTime = TimeUtils.getExpiredTime(hotWordsCacheExpiredTime);
    }

    return hotWordsCache;
  }

  public void markRequest(String keyword) {
    for (String word : keyword.split(" ")) {
      wordsFrequency.getUnchecked(word).incrementAndGet();
    }
  }

  public void cleanHotWords() {
    hotWordsCache.clear();
  }

  public List<Pair<String, Integer>> listHotWordsWithScore(int size) {
    List<Pair<String, Integer>> hotwords = wordsFrequency.asMap().entrySet().stream()
        .sorted((o1, o2) -> (int) (o2.getValue().get() - o1.getValue().get()))
        .map(o -> new Pair(o.getKey(), o.getValue()))
        .collect(Collectors.toList());
    if (size == -1) {
      return hotwords;
    }
    if (size <= hotwords.size()) {
      return hotwords.subList(0, size);
    }
    return hotwords;
  }

  @PostConstruct
  public void init() {
    wordsFrequency = CacheBuilder.newBuilder()
        .build(new CacheLoader<String, AtomicLong>() {
          @Override
          public AtomicLong load(String key) throws Exception {
            return new AtomicLong(0);
          }
        });
  }

  @PreDestroy
  public void uninit() {

  }
}
