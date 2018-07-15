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

  public void putMetadata(String infohash, byte[] metadata) throws DecoderException {
    putMetadata(JTorrentUtils.toInfohashBytes(infohash), metadata);
  }

  public void putMetadata(byte[] infohash, byte[] metadata) {
    PutMetadataRequest request = PutMetadataRequest.newBuilder()
      .setInfohash(ByteString.copyFrom(infohash))
      .setMetadata(ByteString.copyFrom(metadata))
      .build();
    PutMetadataResponse response = getNextStub().putMetadata(request);
  }

  public byte[] getMetadata(String infohash) throws DecoderException {
    return getMetadata(JTorrentUtils.toInfohashBytes(infohash));
  }

  public byte[] getMetadata(byte[] infohash) {
    GetMetadataRequest request = GetMetadataRequest.newBuilder().setInfohash(ByteString.copyFrom(infohash)).build();
    GetMetadataResponse response = getNextStub().getMetadata(request);
    return response.getMetadata().toByteArray();
  }

  public String parseMetadata(String infohash) throws DecoderException {
    return parseMetadata(JTorrentUtils.toInfohashBytes(infohash));
  }

  public String parseMetadata(byte[] infohash) {
    ParseMetadataRequest request = ParseMetadataRequest.newBuilder().setInfohash(ByteString.copyFrom(infohash)).build();
    ParseMetadataResponse response = getNextStub().parseMetadata(request);
    return response.getMetadataJson();
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
