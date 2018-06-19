#!/usr/bin/env bash

work_dir=$(cd "$(dirname "$0")";cd ..;pwd)
cd $work_dir

main_class=com.killxdcj.aiyawocao.meta.crawler.MetaCrawlerMain
jvm_opts="-Xmx1g -Xms1g -XX:+PrintGCDetails -Xloggc:$work_dir/gc.log"
logconf_path=$work_dir/conf/log4j2.xml
crawler_conf=$work_dir/conf/crawler.yaml
lib_path=$work_dir/lib/*

java -server ${jvm_opts} -Dlog4j.configurationFile=file:$logconf_path \
  -cp $lib_path ${main_class} $crawler_conf 1>crawler.out 2>&1