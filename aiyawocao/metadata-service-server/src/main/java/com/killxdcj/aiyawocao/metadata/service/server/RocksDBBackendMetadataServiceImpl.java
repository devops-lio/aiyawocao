package com.killxdcj.aiyawocao.metadata.service.server;

import com.alibaba.fastjson.JSON;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.killxdcj.aiyawocao.bittorrent.bencoding.Bencoding;
import com.killxdcj.aiyawocao.bittorrent.exception.InvalidBittorrentPacketException;
import com.killxdcj.aiyawocao.bittorrent.utils.TimeUtils;
import com.killxdcj.aiyawocao.metadata.service.DoesMetadataExistRequest;
import com.killxdcj.aiyawocao.metadata.service.DoesMetadataExistResponse;
import com.killxdcj.aiyawocao.metadata.service.GetMetadataRequest;
import com.killxdcj.aiyawocao.metadata.service.GetMetadataResponse;
import com.killxdcj.aiyawocao.metadata.service.MetadataServiceGrpc;
import com.killxdcj.aiyawocao.metadata.service.ParseMetadataRequest;
import com.killxdcj.aiyawocao.metadata.service.ParseMetadataResponse;
import com.killxdcj.aiyawocao.metadata.service.PutMetadataRequest;
import com.killxdcj.aiyawocao.metadata.service.PutMetadataResponse;
import com.killxdcj.aiyawocao.metadata.service.server.config.MetadataServiceServerConfig;
import com.killxdcj.aiyawocao.metadata.service.server.config.RocksDBBackendConfig;
import io.grpc.stub.StreamObserver;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.CompactionStyle;
import org.rocksdb.CompressionType;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Statistics;
import org.rocksdb.util.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RocksDBBackendMetadataServiceImpl extends MetadataServiceGrpc.MetadataServiceImplBase {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(RocksDBBackendMetadataServiceImpl.class);
  private static final Logger METADATA = LoggerFactory.getLogger("metadata");
  private static final byte[] DUMMY_VALUE = new byte[0];
  private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  private static final SimpleDateFormat METADATA_SDF = new SimpleDateFormat("yyyyMMdd/HH");

  private byte[] bitMap = null;
  private byte[] existFlag = new byte[]{0x01, 0x02, 0x04, 0x08, 0x10, 0x20, 0x40, (byte)0x80};

  private RocksDBBackendConfig config;
  private RocksDB rocksDB;
  private AtomicInteger totalSize = new AtomicInteger(0);

  private Timer doesExistCosttime;
  private Timer putCosttime;
  private Meter doesExistThroughput;
  private Meter putThroughput;
  private Meter bitfilterHit;
  private Meter existThroughput;

  public RocksDBBackendMetadataServiceImpl(MetadataServiceServerConfig config,
      MetricRegistry registry)
      throws RocksDBException {
    this.config = config.getRocksDBBackendConfig();
    this.rocksDB = buildRocksDB(config.getRocksDBBackendConfig().getRocksDBPath());

    if (this.config.getEnableBitfilter()) {
      bitMap = new byte[0XFFFFFF + 1];
    }
    try (final RocksIterator iterator = rocksDB.newIterator()) {
      if (this.config.getEnableBitfilter()) {
        for (iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
          totalSize.incrementAndGet();
          put(iterator.key());
        }
      } else {
        for (iterator.seekToLast(); iterator.isValid(); iterator.prev()) {
          totalSize.incrementAndGet();
        }
      }
    }
    LOGGER.info("index loaded, {}", totalSize.get());

    registry.register(MetricRegistry.name(RocksDBBackendMetadataServiceImpl.class, "MetadataNum"),
        (Gauge<Integer>) () -> totalSize.get());
    doesExistCosttime = registry.timer(MetricRegistry.name(RocksDBBackendMetadataServiceImpl.class, "doesExist.costtime"));
    putCosttime = registry.timer(MetricRegistry.name(RocksDBBackendMetadataServiceImpl.class, "put.costtime"));
    doesExistThroughput = registry.meter(MetricRegistry.name(RocksDBBackendMetadataServiceImpl.class, "doesExist.throughput"));
    putThroughput = registry.meter(MetricRegistry.name(RocksDBBackendMetadataServiceImpl.class, "put.throughput"));
    bitfilterHit = registry.meter(MetricRegistry.name(RocksDBBackendMetadataServiceImpl.class, "bitfilterHit"));
    existThroughput = registry.meter(MetricRegistry.name(RocksDBBackendConfig.class, "exist.throughput"));
  }

  public void shutdown() {
    rocksDB.close();
  }

  private synchronized void put(byte[] key) {
    int index = ((key[0] & 0xFF) << 16) | ((key[1] & 0xFF) << 8) | (key[2] & 0xFF);
    bitMap[index] = (byte) (bitMap[index] | existFlag[key[3] & 0x07]);
  }

  private boolean mayExist(byte[] key) {
    int index = ((key[0] & 0xFF) << 16) | ((key[1] & 0xFF) << 8) | (key[2] & 0xFF);
    return (bitMap[index] & existFlag[key[3] & 0x07]) != 0;
  }

  private RocksDB buildRocksDB(String rocksDBPath) throws RocksDBException {
    Options options = new Options();
    options.setCreateIfMissing(true)
        .setStatistics(new Statistics())
        .setWriteBufferSize(8 * SizeUnit.KB)
        .setMaxWriteBufferNumber(3)
        .setMaxBackgroundCompactions(10)
        .setCompressionType(CompressionType.SNAPPY_COMPRESSION)
        .setCompactionStyle(CompactionStyle.UNIVERSAL);

    final BlockBasedTableConfig table_options = new BlockBasedTableConfig();
    table_options.setBlockCacheSize(64 * SizeUnit.KB)
        .setFilter(new BloomFilter(10))
        .setCacheNumShardBits(6)
        .setBlockSizeDeviation(5)
        .setBlockRestartInterval(10)
        .setCacheIndexAndFilterBlocks(true)
        .setHashIndexAllowCollision(false)
        .setBlockCacheCompressedSize(64 * SizeUnit.KB)
        .setBlockCacheCompressedNumShardBits(10);
    options.setTableFormatConfig(table_options);

    return RocksDB.open(options, rocksDBPath);
  }

  @Override
  public void doesMetadataExist(DoesMetadataExistRequest request,
      StreamObserver<DoesMetadataExistResponse> responseObserver) {
    long start = TimeUtils.getCurTime();
    try {
      byte[] key = request.getInfohash().toByteArray();
      boolean exist = mayExist(key);
      if (exist) {
        exist = rocksDB.get(request.getInfohash().toByteArray()) != null;
      } else {
        bitfilterHit.mark();
      }

      if (exist) {
        existThroughput.mark();
      }
      DoesMetadataExistResponse response = DoesMetadataExistResponse.newBuilder()
          .setExist(exist)
          .build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (RocksDBException e) {
      LOGGER.error("rocksdb error", e);
      responseObserver.onError(e);
    } finally {
      doesExistCosttime.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
      doesExistThroughput.mark();
    }
  }

  @Override
  public void putMetadata(PutMetadataRequest request,
      StreamObserver<PutMetadataResponse> responseObserver) {
    long start = TimeUtils.getCurTime();
    try {
      String infohash = Hex.encodeHexString(request.getInfohash().toByteArray()).toUpperCase();
      Bencoding bencoding = new Bencoding(request.getMetadata().toByteArray());
      Map<String, Object> metaHuman = (Map<String, Object>) bencoding.decode().toHuman();
      metaHuman.put("infohash", infohash.toUpperCase());
      metaHuman.put("date", SDF.format(new Date()));
      METADATA.info(JSON.toJSONString(metaHuman));
      rocksDB.put(request.getInfohash().toByteArray(), DUMMY_VALUE);
      if (this.config.getEnableBitfilter()) {
        put(request.getInfohash().toByteArray());
      }
      totalSize.incrementAndGet();

      String metadataFile = buildOriginalMetadataPath(infohash);
      try {
        FileUtils.writeByteArrayToFile(new File(metadataFile), request.getMetadata().toByteArray());
      } catch (IOException e) {
        LOGGER.error("save metadta error, " + infohash + ", " + metadataFile, e);
      }

      LOGGER.info("metadata saved, {} -> {}bytes", infohash,
          request.getMetadata().toByteArray().length);
      PutMetadataResponse response = PutMetadataResponse.newBuilder().setResult(true).build();
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    } catch (RocksDBException e) {
      LOGGER.error("rocksdb error", e);
      responseObserver.onError(e);
    } catch (InvalidBittorrentPacketException e) {
      LOGGER.error("decode metadata error", e);
      responseObserver.onError(e);
    } finally {
      putCosttime.update(TimeUtils.getElapseTime(start), TimeUnit.MILLISECONDS);
      putThroughput.mark();
    }
  }

  @Override
  public void getMetadata(GetMetadataRequest request,
      StreamObserver<GetMetadataResponse> responseObserver) {
    super.getMetadata(request, responseObserver);
  }

  @Override
  public void parseMetadata(ParseMetadataRequest request,
      StreamObserver<ParseMetadataResponse> responseObserver) {
    super.parseMetadata(request, responseObserver);
  }

  private String buildOriginalMetadataPath(String infohash) {
    return config.getOriginalMetadataPath() + "/" + METADATA_SDF.format(new Date()) + "/"
        + infohash;
  }
}
