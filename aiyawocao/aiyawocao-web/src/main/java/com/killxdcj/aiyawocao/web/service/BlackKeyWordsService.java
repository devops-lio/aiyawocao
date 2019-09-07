package com.killxdcj.aiyawocao.web.service;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BlackKeyWordsService {
  private static final Logger LOGGER = LoggerFactory.getLogger(BlackKeyWordsService.class);

  private ConcurrentHashMap<String, String> blkKeyWords = new ConcurrentHashMap<>();

  @Value("${blk.keywords.enable}")
  private boolean enable;

  @Value("${blk.keywords.file}")
  private String blackKeyWordsFile;

  public boolean shouldPrevent(String keyword) {
    if (!enable) {
      return false;
    }

    for (String blkKeyword : blkKeyWords.keySet()) {
      if (keyword.contains(blkKeyword)) {
        return true;
      }
    }

    return false;
  }

  @PostConstruct
  public void initBlkKeyWords() {
    if (!enable) {
      return;
    }

    try {
      List<String> keywords = FileUtils.readLines(new File(blackKeyWordsFile), Charset.forName("utf-8"));
      keywords.forEach(keyword -> blkKeyWords.put(keyword, ""));
      LOGGER.info("Load BlkKeywords from file: {}, {}", blackKeyWordsFile, String.join(",", keywords));
    } catch (IOException e) {
      LOGGER.error("Load BlkKeyWords Failed, " + blackKeyWordsFile, e);
    }
  }
}
