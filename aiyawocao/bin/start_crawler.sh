#!/usr/bin/env bash

main_class=com.killxdcj.aiyawocao.meta.crawler.MetaCrawlerMain
jvm_opts="-Xmx1g -Xms1g"

java -server ${jvm_opts} -Dlog4j.configurationFile=file:conf/log4j2.xml \
  -cp "./lib/*" ${main_class} 1>crawler.out 2>&1 &