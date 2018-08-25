package com.killxdcj.aiyawocao.web.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.killxdcj.aiyawocao.common.utils.TimeUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Service;

@Service
public class PredictService {

  private LoadingCache<String, AtomicLong> wordsFrequency;
  private List<String> hotWordsCache = new ArrayList<>();
  private long nextUpdateTime = 0;
  private long hotWordsCacheExpiredTime = 10 * 60 * 1000;

  public List<String> getHotWordsCache() {
    return getHotWords(8);
  }

  public List<String> getHotWords(int size) {
    if (hotWordsCache.size() < size || TimeUtils.getCurTime() > nextUpdateTime) {
      List<String> newHotWords = wordsFrequency.asMap().keySet().stream()
          .sorted((o1, o2) -> (int) (wordsFrequency.getUnchecked(o2).get() - wordsFrequency.getUnchecked(o1).get()))
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
