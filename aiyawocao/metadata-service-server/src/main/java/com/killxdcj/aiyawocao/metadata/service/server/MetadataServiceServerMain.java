package com.killxdcj.aiyawocao.metadata.service.server;

import com.codahale.metrics.MetricRegistry;
import com.killxdcj.aiyawocao.common.metrics.InfluxdbBackendMetrics;
import com.killxdcj.aiyawocao.metadata.service.server.config.MetadataServiceServerConfig;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MetadataServiceServerMain {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetadataServiceServerMain.class);

  private Executor executor;
  private Server server;
  //  private MetadataServiceImpl metadataService;
  private RocksDBBackendMetadataServiceImpl rocksDBBackendMetadataService;

  public static void main(String[] args)
      throws ParseException, IOException, InterruptedException, RocksDBException {
    MetadataServiceServerMain metadataServiceServer = new MetadataServiceServerMain();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> metadataServiceServer.shutdown()));
    metadataServiceServer.start(args);
  }

  public void start(String[] args)
      throws ParseException, IOException, InterruptedException, RocksDBException {
    LOGGER.info("args: {}", Arrays.toString(args));
    CommandLine commandLine = parseCommandLine(args);
    MetadataServiceServerConfig config =
        MetadataServiceServerConfig.fromYamlConfFile(commandLine.getOptionValue("c"));
    LOGGER.info("config: {}", config.toString());

    MetricRegistry metricRegistry =
        InfluxdbBackendMetrics.startMetricReport(config.getInfluxdbBackendMetricsConfig());
//    metadataService = new MetadataServiceImpl(config, metricRegistry);
    rocksDBBackendMetadataService = new RocksDBBackendMetadataServiceImpl(config, metricRegistry);

    executor =
        Executors.newFixedThreadPool(
            config.getExecutorThreadNum(),
            r -> {
              Thread t = new Thread(r);
              t.setDaemon(true);
              t.setName("MetadataService GRPC Thread");
              return t;
            });

    server =
        NettyServerBuilder.forPort(config.getPort())
            .addService(rocksDBBackendMetadataService)
            .executor(executor)
            .maxMessageSize(20 * 1024 * 1024)
            .build();

    server.start();
    LOGGER.info("server started in port : {}", config.getPort());
    server.awaitTermination();
    LOGGER.info("server stoped");
  }

  public void shutdown() {
    LOGGER.info("shutdown server");
    InfluxdbBackendMetrics.shutdown();

    if (server != null) {
      server.shutdown();
    }

    if (rocksDBBackendMetadataService != null) {
      rocksDBBackendMetadataService.shutdown();
    }
  }

  private CommandLine parseCommandLine(String[] args) throws ParseException {
    Options options = new Options();
    Option confOption =
        Option.builder("c")
            .longOpt("configFile")
            .argName("ConfigFile")
            .hasArg(true)
            .desc("ConfigFile path for metadata servier server")
            .required(true)
            .build();
    options.addOption(confOption);

    CommandLineParser parser = new DefaultParser();
    return parser.parse(options, args);
  }
}
