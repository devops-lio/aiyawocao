package com.killxdcj.aiyawocao.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Service
public class RedisPoolService {

  @Value("${redis.host}")
  private String host;

  @Value("${redis.port}")
  private int port;

  private JedisPool jedisPool;

  @PostConstruct
  public void initJedisPool() {
    JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(50);
    jedisPool = new JedisPool(config, host, port);
  }

  @PreDestroy
  public void unInitJedisPool() {
    if (jedisPool != null) {
      jedisPool.close();
    }
  }

  public Jedis getJedis() {
    return jedisPool.getResource();
  }
}
