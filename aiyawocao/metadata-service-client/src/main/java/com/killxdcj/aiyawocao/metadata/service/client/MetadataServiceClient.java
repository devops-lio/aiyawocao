package com.killxdcj.aiyawocao.metadata.service.client;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.protobuf.ByteString;
import com.killxdcj.aiyawocao.bittorrent.bencoding.BencodedString;
import com.killxdcj.aiyawocao.bittorrent.utils.JTorrentUtils;
import com.killxdcj.aiyawocao.bittorrent.utils.TimeUtils;
import com.killxdcj.aiyawocao.metadata.service.DoesMetadataExistRequest;
import com.killxdcj.aiyawocao.metadata.service.DoesMetadataExistResponse;
import com.killxdcj.aiyawocao.metadata.service.DoesMetadatasExistRequest;
import com.killxdcj.aiyawocao.metadata.service.DoesMetadatasExistResponse;
import com.killxdcj.aiyawocao.metadata.service.GetMetadataRequest;
import com.killxdcj.aiyawocao.metadata.service.GetMetadataResponse;
import com.killxdcj.aiyawocao.metadata.service.MetadataServiceGrpc;
import com.killxdcj.aiyawocao.metadata.service.ParseMetadataRequest;
import com.killxdcj.aiyawocao.metadata.service.ParseMetadataResponse;
import com.killxdcj.aiyawocao.metadata.service.PutMetadataRequest;
import com.killxdcj.aiyawocao.metadata.service.PutMetadataResponse;
import com.killxdcj.aiyawocao.metadata.service.RemoveMetadataRequest;
import com.killxdcj.aiyawocao.metadata.service.RemoveMetadataResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.commons.codec.DecoderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataServiceClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetadataServiceClient.class);
  private static final byte NOT_EXIST = 0;
  private static final byte EXIST = 1;
  private static final byte UNKNOW = 2;

  private MetadataServiceClientConfig config;

  private List<GrpcClient> grpcClients;
  private AtomicInteger idx = new AtomicInteger(0);
  private LoadingCache<BencodedString/* infohash */, Byte> localCache;

  private Timer doesMetadataExistTimer;
  private Timer putMetadataTimer;
  private Timer getMetadataTimer;
  private Timer parseMetadataTimer;
  private Timer doesMetadatasExistTimer;

  private Meter doesMetadataExistMeter;
  private Meter doesMetadataExistHitCacheMeter;
  private Meter putMetadataMeter;
  private Meter getMetadataMeter;
  private Meter parseMetadataMeter;
  private Meter doesMetadatasExistMeter;

  public MetadataServiceClient(MetadataServiceClientConfig config, MetricRegistry metricRegistry) {
    this.config = config;

    if (config.getEnableLocalCache()) {
      localCache = CacheBuilder.newBuilder()
          .expireAfterAccess(config.getExpiredTime(), TimeUnit.MILLISECONDS)
          .build(new CacheLoader<BencodedString, Byte>() {
            @Override
            public Byte load(BencodedString bencodedString) throws Exception {
              return UNKNOW;
            }
          });
    }

    grpcClients = new ArrayList<>(config.getPoolsize());
    String[] servers = config.getServer().split(":");
    String host = servers[0];
    int port = Integer.parseInt(servers[1]);
    for (int i = 0; i < config.getPoolsize(); i++) {
      ManagedChannel channel =
          ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
      MetadataServiceGrpc.MetadataServiceBlockingStub stub =
          MetadataServiceGrpc.newBlockingStub(channel);
      grpcClients.add(new GrpcClient(channel, stub));
    }

    doesMetadataExistTimer = metricRegistry
        .timer(MetricRegistry.name(MetadataServiceClient.class, "doesMetadataExist.costtime"));
    putMetadataTimer = metricRegistry
        .timer(MetricRegistry.name(MetadataServiceClient.class, "putMetadata.costtime"));
    getMetadataTimer = metricRegistry
        .timer(MetricRegistry.name(MetadataServiceClient.class, "getMetadata.costtime"));
    parseMetadataTimer = metricRegistry
        .timer(MetricRegistry.name(MetadataServiceClient.class, "parseMetadata.costtime"));
    doesMetadatasExistTimer = metricRegistry
        .timer(MetricRegistry.name(MetadataServiceClient.class, "doesMetadatasExist.costtime"));

    doesMetadataExistMeter = metricRegistry
        .meter(MetricRegistry.name(MetadataServiceClient.class, "doesMetadataExist.throughput"));
    doesMetadataExistHitCacheMeter = metricRegistry.meter(
        MetricRegistry.name(MetadataServiceClient.class, "doesMetadataExist.hitCache.throughput"));
    putMetadataMeter = metricRegistry
        .meter(MetricRegistry.name(MetadataServiceClient.class, "putMetadata.throughput"));
    getMetadataMeter = metricRegistry
        .meter(MetricRegistry.name(MetadataServiceClient.class, "getMetadata.throughput"));
    parseMetadataMeter = metricRegistry
        .meter(MetricRegistry.name(MetadataServiceClient.class, "parseMetadata.throughput"));
    doesMetadatasExistMeter = metricRegistry
        .meter(MetricRegistry.name(MetadataServiceClient.class, "doesMetadatasExist.throughput"));
  }

  public void shutdown() {
    for (GrpcClient client : grpcClients) {
      client.managedChannel.shutdown();
    }
  }

  public boolean doesMetadataExist(String infohash) throws DecoderException {
    return doesMetadataExist(JTorrentUtils.toInfohashBytes(infohash));
  }

  public boolean doesMetadataExist(byte[] infohash) {
    BencodedString bInfohash = null;
    if (config.getEnableLocalCache()) {
      bInfohash = new BencodedString(infohash);
      byte cache = localCache.getUnchecked(bInfohash);
      if (cache != UNKNOW) {
        doesMetadataExistHitCacheMeter.mark();
        return cache == EXIST;
      }
    }

    long start = TimeUtils.getCurTime();
    try {
      DoesMetadataExistRequest request =
          DoesMetadataExistRequest.newBuilder().setInfohash(ByteString.copyFrom(infohash)).build();

      DoesMetadataExistResponse response = getNextStub().doesMetadataExist(request);
      doesMetadataExistMeter.mark();
      if (config.getEnableLocalCache()) {
        localCache.put(bInfohash, response.getExist() ? EXIST : NOT_EXIST);
      }
      return response.getExist();
    } catch (StatusRuntimeException sre) {
      LOGGER.error("doesMetadataExist rpc error", sre);
      return false;
    } finally {
      doesMetadataExistTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
    }
  }

  public List<Boolean> doesMetadatasExist(List<String> infohashs) {
    return doesMetadatasExistReal(infohashs.stream().map(s -> {
      try {
        return JTorrentUtils.toInfohashBytes(s);
      } catch (DecoderException e) {
        return null;
      }
    }).filter(bytes -> bytes != null).collect(Collectors.toList()));
  }

  public List<Boolean> doesMetadatasExistReal(List<byte[]> infohashs) {
    List<BencodedString> toQuery = new ArrayList<>();
    for (byte[] infohash : infohashs) {
      BencodedString bInfohash = new BencodedString(infohash);
      if (config.getEnableLocalCache()) {
        if (localCache.getUnchecked(bInfohash) == UNKNOW) {
          toQuery.add(bInfohash);
        } else {
          doesMetadataExistHitCacheMeter.mark();
        }
      } else {
        toQuery.add(bInfohash);
      }
    }

    long start = TimeUtils.getCurTime();
    try {
      DoesMetadatasExistRequest request = DoesMetadatasExistRequest.newBuilder()
          .addAllInfohash(toQuery.stream().map(s -> ByteString.copyFrom(s.asBytes()))
              .collect(Collectors.toList())).build();
      DoesMetadatasExistResponse response = getNextStub().doesMetadatasExist(request);
      doesMetadatasExistMeter.mark();
      if (config.getEnableLocalCache()) {
        List<Boolean> exists = response.getExistList();
        for (int i = 0; i < toQuery.size(); i++) {
          localCache.put(toQuery.get(i), exists.get(i) ? EXIST : NOT_EXIST);
        }
        List<Boolean> rets = new ArrayList<>();
        for (byte[] infohash : infohashs) {
          byte cache = localCache.getUnchecked(new BencodedString(infohash));
          if (cache == UNKNOW) {
            LOGGER.warn("batch query local cache unexist");
          }

          rets.add(cache == EXIST);
        }
        return rets;
      } else {
        return response.getExistList();
      }
    } catch (StatusRuntimeException sre) {
      LOGGER.error("doesMetadatasExist rpc error", sre);
      return null;
    } finally {
      doesMetadatasExistTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
    }
  }

  public void putMetadata(String infohash, byte[] metadata) throws Throwable {
    putMetadata(JTorrentUtils.toInfohashBytes(infohash), metadata);
  }

  public void putMetadata(byte[] infohash, byte[] metadata) throws Throwable {
    long start = TimeUtils.getCurTime();
    try {
      PutMetadataRequest request =
          PutMetadataRequest.newBuilder()
              .setInfohash(ByteString.copyFrom(infohash))
              .setMetadata(ByteString.copyFrom(metadata))
              .build();
      PutMetadataResponse response = getNextStub().putMetadata(request);
      putMetadataMeter.mark();
      localCache.put(new BencodedString(infohash), EXIST);
    } catch (StatusRuntimeException sre) {
      throw sre;
    } finally {
      putMetadataTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
    }
  }

  public byte[] getMetadata(String infohash) throws Throwable {
    return getMetadata(JTorrentUtils.toInfohashBytes(infohash));
  }

  public byte[] getMetadata(byte[] infohash) throws Throwable {
    long start = TimeUtils.getCurTime();
    try {
      GetMetadataRequest request =
          GetMetadataRequest.newBuilder().setInfohash(ByteString.copyFrom(infohash)).build();
      GetMetadataResponse response = getNextStub().getMetadata(request);
      getMetadataMeter.mark();
      return response.getMetadata().toByteArray();
    } catch (StatusRuntimeException sre) {
      throw sre.getCause();
    } finally {
      getMetadataTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
    }
  }

  public String parseMetadata(String infohash) throws Throwable {
    return parseMetadata(JTorrentUtils.toInfohashBytes(infohash));
  }

  public String parseMetadata(byte[] infohash) throws Throwable {
    long start = TimeUtils.getCurTime();
    try {
      ParseMetadataRequest request =
          ParseMetadataRequest.newBuilder().setInfohash(ByteString.copyFrom(infohash)).build();
      ParseMetadataResponse response = getNextStub().parseMetadata(request);
      parseMetadataMeter.mark();
      return response.getMetadataJson();
    } catch (StatusRuntimeException sre) {
      throw sre.getCause();
    } finally {
      parseMetadataTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
    }
  }

  public void removeMetadata(byte[] infohash) throws Throwable {
    try {
      RemoveMetadataRequest request = RemoveMetadataRequest.newBuilder()
          .setInfohash(ByteString.copyFrom(infohash)).build();
      RemoveMetadataResponse response = getNextStub().removeMetadata(request);
    } catch (StatusRuntimeException sre) {
      throw sre.getCause();
    }
  }

  private MetadataServiceGrpc.MetadataServiceBlockingStub getNextStub() {
    int curIdx = idx.getAndIncrement();
    int tarIdx = curIdx % grpcClients.size();
    if (curIdx >= grpcClients.size()) {
      idx.compareAndSet(curIdx + 1, tarIdx + 1);
    }
    return grpcClients.get(tarIdx).stub;
  }

  private class GrpcClient {

    private ManagedChannel managedChannel;
    private MetadataServiceGrpc.MetadataServiceBlockingStub stub;

    public GrpcClient(
        ManagedChannel managedChannel, MetadataServiceGrpc.MetadataServiceBlockingStub stub) {
      this.managedChannel = managedChannel;
      this.stub = stub;
    }
  }
}
