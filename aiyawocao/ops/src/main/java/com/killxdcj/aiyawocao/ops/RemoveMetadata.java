package com.killxdcj.aiyawocao.ops;

import com.codahale.metrics.MetricRegistry;
import com.google.protobuf.ByteString;
import com.killxdcj.aiyawocao.metadata.service.DoesMetadataExistRequest;
import com.killxdcj.aiyawocao.metadata.service.DoesMetadataExistResponse;
import com.killxdcj.aiyawocao.metadata.service.client.MetadataServiceClient;
import com.killxdcj.aiyawocao.metadata.service.client.MetadataServiceClientConfig;
import java.io.File;
import java.io.IOException;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

public class RemoveMetadata {
  private Namespace nameSpace;

  public static void main(String[] args) {
    RemoveMetadata removeMetadata = new RemoveMetadata();
    removeMetadata.start(args);
  }

  public void start(String[] args) {
    ArgumentParser parser = buildParser();
    try {
      nameSpace = parser.parseArgs(args);
      switch (nameSpace.getString("action")) {
        case "remove":
          remove();
          break;
        case "exist":
          exist();
          break;
          default:
            break;
      }
    } catch (ArgumentParserException e) {
      parser.handleError(e);
    }
  }

  public void remove() {
    long cnt = 0;
    String file = nameSpace.getString("file");
    MetadataServiceClientConfig config = new MetadataServiceClientConfig();
    config.setServer(nameSpace.getString("btproxy"));
    MetadataServiceClient client = new MetadataServiceClient(config, new MetricRegistry());
    try {
      LineIterator lineIterator = FileUtils.lineIterator(new File(file));
      while (lineIterator.hasNext()) {
        client.removeMetadata(Hex.decodeHex(lineIterator.nextLine().toCharArray()));
        cnt++;
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (DecoderException e) {
      e.printStackTrace();
    } catch (Throwable throwable) {
      throwable.printStackTrace();
    }
    System.out.println("finished, " + cnt);
  }

  public void exist() {
    MetadataServiceClientConfig config = new MetadataServiceClientConfig();
    config.setServer(nameSpace.getString("btproxy"));
    MetadataServiceClient client = new MetadataServiceClient(config, new MetricRegistry());

    try {
      boolean exist = client.doesMetadataExist(nameSpace.getString("infohash"));
      System.out.println(nameSpace.getString("infohash") + ":" + exist);
    } catch (DecoderException e) {
      e.printStackTrace();
    }
  }

  public ArgumentParser buildParser() {
    ArgumentParser parser =
        ArgumentParsers.newFor("MetadataAdmin").build().defaultHelp(true).description("Metadata Admin Utils");

    Subparsers subparsers = parser.addSubparsers().title("actions");
    Subparser remove =
        subparsers
            .addParser("remove")
            .setDefault("action", "remove")
            .defaultHelp(true)
            .help("Remove Metadata");
    remove.addArgument("-f", "--file").required(true).help("File path, contains infohash");
    remove.addArgument("-b", "--btproxy").required(true).help("BTProxy, host:port");

    Subparser exist = subparsers.addParser("exist")
        .setDefault("action", "exist")
        .defaultHelp(true)
        .help("Does Metadata exist");
    exist.addArgument("-i", "--infohash").required(true).help("infohash");
    exist.addArgument("-b", "--btproxy").required(true).help("BTProxy, host:port");

    return parser;
  }
}
