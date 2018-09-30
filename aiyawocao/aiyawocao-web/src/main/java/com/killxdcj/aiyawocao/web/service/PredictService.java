package com.killxdcj.aiyawocao.web.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.killxdcj.aiyawocao.common.utils.TimeUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import javafx.util.Pair;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

@Service
public class PredictService {
  private static final Logger LOGGER = LoggerFactory.getLogger(PredictService.class);

  @Autowired
  private RedisPoolService redisPoolService;

  private LoadingCache<String, AtomicLong> wordsFrequency;
  private List<String> hotWordsCache = new ArrayList<>();
  private long nextUpdateTime = 0;
  private long hotWordsCacheExpiredTime = 60 * 1000;

  private static final List<String> blkHotWords = new ArrayList(){{
    add("奸");
    add("强");
    add("暴");
    add("幼");
    add("萝");
    add("小");
    add("正");
  }};

  public List<String> getHotWordsCache() {
    return getHotWordsFromCache(8);
  }

  public List<String> getHotSearch() {
    List<String> hotSearch = getHotSearchFromRedis();
    if (hotSearch.size() == 0) {
      hotSearch = getHotWordsFromCache(10);
    }
    return hotSearch;
  }

  public List<String> getHotSearchFromRedis() {
    try (Jedis jedis = redisPoolService.getJedis()) {
      return jedis.lrange("hot_search_all", 0,9);
    } catch (Throwable t) {
      LOGGER.error("get hot search from redis error", t);
      return Collections.EMPTY_LIST;
    }
  }

  public List<String> getHotWordsFromCache(int size) {
    if (hotWordsCache.size() < size || TimeUtils.getCurTime() > nextUpdateTime) {
      List<String> newHotWords = wordsFrequency.asMap().keySet().stream()
          .sorted((o1, o2) -> (int) (wordsFrequency.getUnchecked(o2).get() - wordsFrequency.getUnchecked(o1).get()))
          .filter(s -> {
            if (s.length() < 2) {
              return false;
            }

            for (String blk : blkHotWords) {
              if (s.indexOf(blk) != -1) {
                return false;
              }
            }
            return true;
          })
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
    wordsFrequency = CacheBuilder.newBuilder()
        .build(new CacheLoader<String, AtomicLong>() {
          @Override
          public AtomicLong load(String key) throws Exception {
            return new AtomicLong(0);
          }
        });
    hotWordsCache.clear();
  }

  public List<Pair<String, Integer>> listHotWordsWithScore(int size) {
    List<Pair<String, Integer>> hotwords = wordsFrequency.asMap().entrySet().stream()
        .sorted((o1, o2) -> (int) (o2.getValue().get() - o1.getValue().get()))
        .map(o -> new Pair<>(o.getKey(), (int) o.getValue().get()))
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
