#!/usr/bin/env bash
java -cp metadata-indexer-1.1-SNAPSHOT.jar com.killxdcj.aiyawocao.metadata.indexer.ArchivedJsonFileIndexer -e dev.new:9200 -i test1 -f /Users/caojianhua/Workspace/meta-crawler/metadata/metadata.log
