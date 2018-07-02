package com.killxdcj.aiyawocao.meta.manager;

import com.alibaba.fastjson.JSON;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.killxdcj.aiyawocao.meta.manager.config.MetaManagerConfig;
import com.killxdcj.aiyawocao.meta.manager.exception.MetaNotExistException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StreamCorruptedException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class MetaCentreBackendMetaManager extends MetaManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetaCentreBackendMetaManager.class);

    private HttpClient client;
    private MetaManagerConfig config;
    private String existUrl;
    private String putUrl;
    private ConcurrentMap<String, Object> infohashCache = new ConcurrentHashMap<>();
    private Timer requestTimer;

    public MetaCentreBackendMetaManager(MetricRegistry metricRegistry, MetaManagerConfig config) {
        super(metricRegistry);
        this.config = config;
        client = HttpClients.custom()
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(100)
                .build();
        existUrl = String.format("http://%s/meta/%s/exist", config.getMetaCentreAddr(), "%s");
        putUrl = String.format("http://%s/meta/%s/put", config.getMetaCentreAddr(), "%s");

        PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
        connManager.setMaxTotal(200);

        requestTimer = metricRegistry.timer(MetricRegistry.name(MetaCentreBackendMetaManager.class, "request"));
    }

    @Override
    public void shutdown() {
    }

    @Override
    public boolean doesMetaExist(String infohash) {
        String infohashL = infohash.toLowerCase();
        if (infohashCache.containsKey(infohashL)) {
            return true;
        }

        long start = System.currentTimeMillis();
        HttpGet get = new HttpGet(String.format(existUrl, infohash));
        try {
            HttpResponse resp = client.execute(get);
            Map<String, Object> result = JSON.parseObject(EntityUtils.toString(resp.getEntity()), Map.class);
            boolean exist = (boolean)result.getOrDefault("exist", false);
            if (exist) {
                infohashCache.put(infohashL, new Object());
            }
            return exist;
        } catch (IOException e) {
            LOGGER.error("query metacentre error", e);
            return false;
        } finally {
            get.releaseConnection();
            requestTimer.update(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public boolean doesMetaExist(String infohash, boolean forceCheckOSS) {
        return doesMetaExist(infohash);
    }

    @Override
    public void put(String infohash, byte[] meta) {
        long start = System.currentTimeMillis();
        HttpPost post = new HttpPost(String.format(putUrl, infohash));
        try {
            post.setEntity(new ByteArrayEntity(meta));
            HttpResponse resp = client.execute(post);
            if (resp.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                LOGGER.error("put metadata error, " + infohash);
            } else {
                String infohashL = infohash.toLowerCase();
                infohashCache.put(infohashL, new Object());
            }
        } catch (IOException e) {
            LOGGER.error("put metadata error" + infohash, e);
        } finally {
            post.releaseConnection();
            requestTimer.update(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public byte[] get(String infohash) throws MetaNotExistException, IOException {
        return new byte[0];
    }
}
