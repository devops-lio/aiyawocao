package com.killxdcj.aiyawocao.metadata.indexer;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.killxdcj.aiyawocao.metadata.indexer.config.ESIndexerConfig;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class ArchivedJsonFileIndexer {

  public static void main(String[] args) throws ParseException {
    ArchivedJsonFileIndexer indexer = new ArchivedJsonFileIndexer();
    indexer.start(args);
  }

  public void start(String[] args) throws ParseException {
    CommandLine commandLine = parseCommandLine(args);

    String[] esAddrs = commandLine.getOptionValue("e").split(":");

    ESIndexerConfig config = new ESIndexerConfig();
    config.setHostname(esAddrs[0]);
    config.setPort(Integer.parseInt(esAddrs[1]));
    config.setIndex(commandLine.getOptionValue("i"));

    File file = new File(commandLine.getOptionValue("f"));
    if (!file.exists() || !file.isFile()) {
      System.out.println("archives error : " + file);
      System.exit(-1);
    }

    String type = commandLine.getOptionValue("t");

    MetricRegistry registry = new MetricRegistry();
    ConsoleReporter reporter = ConsoleReporter.forRegistry(registry)
        .convertDurationsTo(TimeUnit.MILLISECONDS)
        .convertRatesTo(TimeUnit.SECONDS)
        .build();
    reporter.start(1, TimeUnit.MINUTES);

    Timer cost = registry.timer("cost");
    Meter meter = registry.meter("meter");

    try (ESBackendIndexerClient client = new ESBackendIndexerClient(config);
         InputStreamReader in = new InputStreamReader(new FileInputStream(file), "utf8");
         BufferedReader reader = new BufferedReader(in)) {
      String line;
      long start;
      while ((line = reader.readLine()) != null) {
        try {
          start = System.currentTimeMillis();
          client.index(type, line);
          cost.update(System.currentTimeMillis() - start, TimeUnit.MILLISECONDS);
          meter.mark();
        } catch (Exception e) {
          System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
          System.out.println(line);
          e.printStackTrace();
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private CommandLine parseCommandLine(String[] args) throws ParseException {
    Options options = new Options();
    Option fileOP = Option.builder("f")
        .longOpt("file")
        .argName("ArchivedFile")
        .hasArg(true)
        .desc("Archived JsonFile Path")
        .required(true)
        .build();
    options.addOption(fileOP);

    Option esAddrOP = Option.builder("e")
        .longOpt("es")
        .argName("ESAddr")
        .hasArg(true)
        .desc("ES addr")
        .required(true)
        .build();
    options.addOption(esAddrOP);

    Option indexOP = Option.builder("i")
        .longOpt("index")
        .argName("index")
        .hasArg(true)
        .desc("index name")
        .required(true)
        .build();
    options.addOption(indexOP);

    Option typeOP = Option.builder("t")
        .longOpt("type")
        .argName("type")
        .hasArg(true)
        .desc("type")
        .required(true)
        .build();
    options.addOption(typeOP);

    CommandLineParser parser = new DefaultParser();
    return parser.parse(options, args);
  }
}
