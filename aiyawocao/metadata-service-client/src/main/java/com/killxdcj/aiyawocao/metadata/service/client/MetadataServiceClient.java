package com.killxdcj.aiyawocao.metadata.service.client;

import com.google.protobuf.ByteString;
import com.killxdcj.aiyawocao.bittorrent.utils.JTorrentUtils;
import com.killxdcj.aiyawocao.metadata.service.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
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

  public MetadataServiceClient(MetadataServiceClientConfig config) {
    this.config = config;

    grpcClients = new ArrayList<>(config.getPoolsize());
    String[] servers = config.getServer().split(":");
    String host = servers[0];
    int port = Integer.parseInt(servers[1]);
    for (int i = 0; i < config.getPoolsize(); i++) {
      ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext(true).build();
      MetadataServiceGrpc.MetadataServiceBlockingStub stub = MetadataServiceGrpc.newBlockingStub(channel);
      grpcClients.add(new GrpcClient(channel, stub));
    }
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
    try {
      DoesMetadataExistRequest request = DoesMetadataExistRequest.newBuilder()
        .setInfohash(ByteString.copyFrom(infohash))
        .build();

      DoesMetadataExistResponse response = getNextStub().doesMetadataExist(request);
      return response.getExist();
    } catch (StatusRuntimeException sre) {
      LOGGER.error("doesMetadataExist rpc error", sre);
      return false;
    }
  }

  public void putMetadata(String infohash, byte[] metadata) throws Throwable {
    putMetadata(JTorrentUtils.toInfohashBytes(infohash), metadata);
  }

  public void putMetadata(byte[] infohash, byte[] metadata) throws Throwable {
    try {
      PutMetadataRequest request = PutMetadataRequest.newBuilder()
        .setInfohash(ByteString.copyFrom(infohash))
        .setMetadata(ByteString.copyFrom(metadata))
        .build();
      PutMetadataResponse response = getNextStub().putMetadata(request);
    } catch (StatusRuntimeException sre) {
      throw sre.getCause();
    }
  }

  public byte[] getMetadata(String infohash) throws Throwable {
    return getMetadata(JTorrentUtils.toInfohashBytes(infohash));
  }

  public byte[] getMetadata(byte[] infohash) throws Throwable {
    try {
      GetMetadataRequest request = GetMetadataRequest.newBuilder().setInfohash(ByteString.copyFrom(infohash)).build();
      GetMetadataResponse response = getNextStub().getMetadata(request);
      return response.getMetadata().toByteArray();
    } catch (StatusRuntimeException sre) {
      throw sre.getCause();
    }
  }

  public String parseMetadata(String infohash) throws Throwable {
    return parseMetadata(JTorrentUtils.toInfohashBytes(infohash));
  }

  public String parseMetadata(byte[] infohash) throws Throwable {
    try {
      ParseMetadataRequest request = ParseMetadataRequest.newBuilder().setInfohash(ByteString.copyFrom(infohash)).build();
      ParseMetadataResponse response = getNextStub().parseMetadata(request);
      return response.getMetadataJson();
    } catch (StatusRuntimeException sre) {
      throw sre.getCause();
    }
  }

  private class GrpcClient {
    private ManagedChannel managedChannel;
    private MetadataServiceGrpc.MetadataServiceBlockingStub stub;

    public GrpcClient(ManagedChannel managedChannel, MetadataServiceGrpc.MetadataServiceBlockingStub stub) {
      this.managedChannel = managedChannel;
      this.stub = stub;
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
}
