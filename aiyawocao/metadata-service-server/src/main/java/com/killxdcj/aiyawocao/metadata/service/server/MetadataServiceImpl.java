package com.killxdcj.aiyawocao.metadata.service.server;

import com.alibaba.fastjson.JSON;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.utils.JTorrentUtils;
import com.killxdcj.aiyawocao.metadata.service.*;
import com.killxdcj.aiyawocao.metadata.service.server.config.AliOSSBackendConfig;
import com.killxdcj.aiyawocao.metadata.service.server.config.MetadataServiceServerConfig;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class MetadataServiceImpl extends MetadataServiceGrpc.MetadataServiceImplBase {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetadataServiceImpl.class);

  private static final Object objDummy = new Object();
  private MetadataServiceServerConfig config;
  private AliOSSBackendConfig ossBackendConfig;
  private OSSClient ossClient;

  private ConcurrentMap<BencodedString, Object> indexAll;
  private ConcurrentMap<BencodedString, Object> indexSplited;
  private volatile int indexSplitedLastSize = 0;
  private AtomicInteger totalSize = new AtomicInteger(0);

  private Thread indexSaver;

  public MetadataServiceImpl(MetadataServiceServerConfig config, MetricRegistry metricRegistry) {
    this(config);

    metricRegistry.register(MetricRegistry.name(MetadataServiceImpl.class, "MetadataNum"),
      (Gauge<Integer>) () -> totalSize.get());
  }

  public MetadataServiceImpl(MetadataServiceServerConfig config) {
    this.config = config;
    this.ossBackendConfig = config.getAliOSSBackendConfig();

    AliOSSBackendConfig ossBackendConfig = config.getAliOSSBackendConfig();
    ossClient = new OSSClient(ossBackendConfig.getEndpoint(), ossBackendConfig.getAccessKeyId(),
      ossBackendConfig.getAccessKeySecret());
    loadIndex();
    startIndexSaver();
  }

  public void shutdown() {
    indexSaver.interrupt();
    saveIndex();
    ossClient.shutdown();
    LOGGER.info("MetadataServiceImpl stoped");
  }

  private void loadIndex() {
    indexAll = new ConcurrentHashMap<>();
    indexSplited = new ConcurrentHashMap<>();

    AliOSSBackendConfig ossBackendConfig = config.getAliOSSBackendConfig();
    ListObjectsRequest request = new ListObjectsRequest(ossBackendConfig.getBucketName())
      .withPrefix(ossBackendConfig.getIndexRoot())
      .withMaxKeys(1000);
    ObjectListing objs = ossClient.listObjects(request);
    LOGGER.info("loading metadata index, size:{}", objs.getObjectSummaries().size());
    for (OSSObjectSummary summary : ossClient.listObjects(request).getObjectSummaries()) {
      int size = 0;
      String indexFile = summary.getKey();
      boolean isLatest = indexFile.equals(ossBackendConfig.getIndexRoot() + "/" + ossBackendConfig.getIndexPrefix());
      OSSObject ossObject = ossClient.getObject(ossBackendConfig.getBucketName(), indexFile);
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(ossObject.getObjectContent()))) {
        String line = reader.readLine();
        while (line != null) {
          BencodedString infohash = new BencodedString(JTorrentUtils.toInfohashBytes(line));
          indexAll.put(infohash, objDummy);
          if (isLatest) {
            indexSplited.put(infohash, objDummy);
          }
          line = reader.readLine();
          size++;
          totalSize.incrementAndGet();
        }
        LOGGER.info("loaded metadata splited index file, {} -> {}", indexFile, size);
      } catch (Exception e) {
        throw new RuntimeException("load metadata index error", e);
      }
    }
    indexSplitedLastSize = indexSplited.size();
    LOGGER.info("metadata index loaded, {}, allSize:{}, splitedSize:{}", ossBackendConfig.getIndexRoot(), indexAll.size(),
      indexSplitedLastSize);
  }

  private void saveIndex() {
    if (indexSplited == null || indexSplited.size() == 0) {
      return;
    }

    int newSize = indexSplited.size();
    if (newSize == indexSplitedLastSize) {
      return;
    }

    LOGGER.info("start sava metadata index, {} -> {}", indexSplitedLastSize, newSize);
    AliOSSBackendConfig ossBackendConfig = config.getAliOSSBackendConfig();
    ConcurrentMap<BencodedString, Object> indexSplitedOld = indexSplited;
    try {
      String indexFile = ossBackendConfig.getIndexRoot() + "/" + ossBackendConfig.getIndexPrefix();
      if (newSize > ossBackendConfig.getMaxIndexSize()) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMddHHmmss");
        indexFile = indexFile + fmt.format(new Date());
        indexSplited = new ConcurrentHashMap<>();
      }

      StringBuilder sb = new StringBuilder();
      boolean first = true;
      for (BencodedString infohash : indexSplitedOld.keySet()) {
        if (!first) {
          sb.append("\n");
        }
        sb.append(infohash.asHexString().toUpperCase());
      }

      ossClient.putObject(ossBackendConfig.getBucketName(), indexFile, new ByteArrayInputStream(sb.toString().getBytes()));
      LOGGER.info("metadata index saved, {}, size:{}", indexFile, newSize);

      if (newSize > ossBackendConfig.getMaxIndexSize()) {
        ossClient.putObject(ossBackendConfig.getBucketName(), ossBackendConfig.getIndexRoot() + "/" + ossBackendConfig.getIndexPrefix(),
          new ByteArrayInputStream(new byte[0]));
        indexSplitedLastSize = 0;
        LOGGER.info("metadata index splited");
      } else {
        indexSplitedLastSize = newSize;
      }
    } catch (Throwable t) {
      LOGGER.error("sava metadata index error", t);
      indexSplited.putAll(indexSplitedOld);
    }
  }

  public void startIndexSaver() {
    indexSaver = new Thread(() -> {
      Thread.currentThread().setName("Metadata Index Saver Thread");
      while (true) {
        try {
          Thread.sleep(config.getAliOSSBackendConfig().getIndexSaveInterval());
          saveIndex();
        } catch (InterruptedException e) {
          return;
        } catch (Throwable t) {
          LOGGER.error("save metadata index error", t);
        }
      }
    });
    indexSaver.start();
  }

  @Override
  public void doesMetadataExist(DoesMetadataExistRequest request, StreamObserver<DoesMetadataExistResponse> responseObserver) {
    DoesMetadataExistResponse response = DoesMetadataExistResponse.newBuilder()
      .setExist(indexAll.containsKey(new BencodedString(request.getInfohash().toByteArray())))
      .build();

    responseObserver.onNext(response);
    responseObserver.onCompleted();
  }

  @Override
  public void putMetadata(PutMetadataRequest request, StreamObserver<PutMetadataResponse> responseObserver) {
    BencodedString infohash = new BencodedString(request.getInfohash().toByteArray());
    byte[] metadata = request.getMetadata().toByteArray();
    String infohashStr = infohash.asHexString();
    ossClient.putObject(ossBackendConfig.getBucketName(), buildBucketKey(infohashStr), new ByteArrayInputStream(metadata));
    LOGGER.info("metadata saved, {} -> {}bytes", infohashStr, metadata.length);

    indexAll.put(infohash, objDummy);
    indexSplited.put(infohash, objDummy);

    PutMetadataResponse response = PutMetadataResponse.newBuilder().setResult(true).build();
    responseObserver.onNext(response);
    responseObserver.onCompleted();
    totalSize.incrementAndGet();
  }

  @Override
  public void getMetadata(GetMetadataRequest request, StreamObserver<GetMetadataResponse> responseObserver) {
    BencodedString infohash = new BencodedString(request.getInfohash().toByteArray());
    try {
      byte[] metadata = getMetadataInternal(infohash);
      GetMetadataResponse response = GetMetadataResponse.newBuilder()
        .setMetadata(ByteString.copyFrom(metadata))
        .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.error("get metadata error, " + infohash.asHexString(), e);
      }
      responseObserver.onError(e);
    }
  }

  @Override
  public void parseMetadata(ParseMetadataRequest request, StreamObserver<ParseMetadataResponse> responseObserver) {
    BencodedString infohash = new BencodedString(request.getInfohash().toByteArray());
    try {
      byte[] metadata = getMetadataInternal(infohash);
      Bencoding bencoding = new Bencoding(metadata);
      String metadataJson = JSON.toJSONString(bencoding.decode().toHuman());
      ParseMetadataResponse response = ParseMetadataResponse.newBuilder()
        .setMetadataJson(metadataJson)
        .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (Exception e) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.error("parse metadata error, " + infohash.asHexString(), e);
      }
      responseObserver.onError(e);
    }
  }

  private byte[] getMetadataInternal(BencodedString infohash) throws Exception {
    if (!indexAll.containsKey(infohash)) {
      throw new Exception("metadata not exist");
    }

    String key = buildBucketKey(infohash.asHexString());
    if (!ossClient.doesObjectExist(ossBackendConfig.getBucketName(), key)) {
      throw new Exception("metadata exist in index but not exist in oss");
    }

    try (InputStream in = ossClient.getObject(ossBackendConfig.getBucketName(), key).getObjectContent()) {
      int length = in.available();
      byte[] metadata = new byte[length];
      int idx = 0;
      while (length > 0) {
        int read = in.read(metadata, idx, length);
        idx += read;
        length -= read;
      }

      return metadata;
    }
  }

  private String buildBucketKey(String infohash) {
    infohash = infohash.toUpperCase();
    return "" + infohash.charAt(0) + infohash.charAt(1) + "/" +
      infohash.charAt(2) + infohash.charAt(3) + "/" +
      infohash.charAt(4) + infohash.charAt(5) + "/" +
      infohash;
  }
}
