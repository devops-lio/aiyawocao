package com.killxdcj.aiyawocao.metadata.service.server;

import com.killxdcj.aiyawocao.metadata.service.server.config.MetadataServiceServerConfig;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MetadataServiceServerMain {
  private static final Logger LOGGER = LoggerFactory.getLogger(MetadataServiceServerMain.class);

  private Executor executor;
  private Server server;
  private MetadataServiceImpl metadataService;

  public void start(String[] args) throws ParseException, IOException, InterruptedException {
    LOGGER.info("args: {}", Arrays.toString(args));
    CommandLine commandLine = parseCommandLine(args);
    MetadataServiceServerConfig config = MetadataServiceServerConfig.fromYamlConfFile(commandLine.getOptionValue("c"));
    LOGGER.info("config: {}", config.toString());

    metadataService = new MetadataServiceImpl(config);

    executor = Executors.newFixedThreadPool(config.getExecutorThreadNum(), r -> {
      Thread t = new Thread(r);
      t.setDaemon(true);
      t.setName("MetadataService GRPC Thread");
      return t;
    });

    server = NettyServerBuilder.forPort(config.getPort())
      .addService(metadataService)
      .executor(executor)
      .build();

    server.start();
    LOGGER.info("server started in port : {}", config.getPort());
    server.awaitTermination();
    LOGGER.info("server stoped");
  }

  public void shutdown() {
    LOGGER.info("shutdown server");
    if (server != null) {
      server.shutdown();
    }

    if (metadataService != null) {
      metadataService.shutdown();
    }
  }

  private CommandLine parseCommandLine(String[] args) throws ParseException {
    Options options = new Options();
    Option confOption = Option.builder("c")
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

  public static void main(String[] args) throws ParseException, IOException, InterruptedException {
    MetadataServiceServerMain metadataServiceServer = new MetadataServiceServerMain();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> metadataServiceServer.shutdown()));
    metadataServiceServer.start(args);
  }
}
