package com.killxdcj.aiyawocao.web.service;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.JiebaSegmenter.SegMode;
import com.huaban.analysis.jieba.WordDictionary;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JiebaService {

  @Value("${jieba.dicts.path}")
  private String userDictsPath;

  private JiebaSegmenter jiebaSegmenter;

  public List<String> analyze(String content) {
    return jiebaSegmenter.process(content, SegMode.SEARCH).stream()
        .map(r -> r.word)
        .sorted((o1, o2) -> o2.length() - o1.length())
        .filter(s -> s.length() > 2)
        .collect(Collectors.toList());
  }

  @PostConstruct
  public void initJiebaSegmenter() {
    WordDictionary.getInstance().init(new File(userDictsPath).toPath());
    jiebaSegmenter = new JiebaSegmenter();
  }
}
