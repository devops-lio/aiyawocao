package com.killxdcj.aiyawocao.metadata.service.client;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.protobuf.ByteString;
import com.killxdcj.aiyawocao.bittorrent.utils.JTorrentUtils;
import com.killxdcj.aiyawocao.bittorrent.utils.TimeUtils;
import com.killxdcj.aiyawocao.metadata.service.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.TimeUnit;
import org.apache.commons.codec.DecoderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MetadataServiceClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetadataServiceClient.class);
  private MetadataServiceClientConfig config;

  private List<GrpcClient> grpcClients;
  private AtomicInteger idx = new AtomicInteger(0);

  private Timer doesMetadataExistTimer;
  private Timer putMetadataTimer;
  private Timer getMetadataTimer;
  private Timer parseMetadataTimer;

  private Meter doesMetadataExistMeter;
  private Meter putMetadataMeter;
  private Meter getMetadataMeter;
  private Meter parseMetadataMeter;

  public MetadataServiceClient(MetadataServiceClientConfig config, MetricRegistry metricRegistry) {
    this.config = config;

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

    doesMetadataExistTimer = metricRegistry.timer(MetricRegistry.name(MetadataServiceClient.class, "doesMetadataExist.costtime"));
    putMetadataTimer = metricRegistry.timer(MetricRegistry.name(MetadataServiceClient.class, "putMetadataTimer.costtime"));
    getMetadataTimer = metricRegistry.timer(MetricRegistry.name(MetadataServiceClient.class, "getMetadataTimer.costtime"));
    parseMetadataTimer = metricRegistry.timer(MetricRegistry.name(MetadataServiceClient.class, "parseMetadataTimer.costtime"));

    doesMetadataExistMeter = metricRegistry.meter(MetricRegistry.name(MetadataServiceClient.class, "doesMetadataExist.throughput"));
    putMetadataMeter = metricRegistry.meter(MetricRegistry.name(MetadataServiceClient.class, "putMetadataTimer.throughput"));
    getMetadataMeter = metricRegistry.meter(MetricRegistry.name(MetadataServiceClient.class, "getMetadataTimer.throughput"));
    parseMetadataMeter = metricRegistry.meter(MetricRegistry.name(MetadataServiceClient.class, "parseMetadataTimer.throughput"));
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
    long start = TimeUtils.getCurTime();
    try {
      DoesMetadataExistRequest request =
          DoesMetadataExistRequest.newBuilder().setInfohash(ByteString.copyFrom(infohash)).build();

      DoesMetadataExistResponse response = getNextStub().doesMetadataExist(request);
      doesMetadataExistMeter.mark();
      return response.getExist();
    } catch (StatusRuntimeException sre) {
      LOGGER.error("doesMetadataExist rpc error", sre);
      return false;
    } finally {
      doesMetadataExistTimer.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
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
    } catch (StatusRuntimeException sre) {
      throw sre.getCause();
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
