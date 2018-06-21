#!/usr/bin/env bash

work_dir=$(cd "$(dirname "$0")";cd ..;pwd)
cd $work_dir

export JAVA_HOME=real_java_home
export PATH=$PATH:$JAVA_HOME/bin
export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar

main_class=com.killxdcj.aiyawocao.meta.crawler.MetaCrawlerMain
jvm_opts="-Xmx350m -Xms350m -XX:+PrintGCDateStamps -XX:+PrintGCDetails -Xloggc:$work_dir/gc.log"
logconf_path=$work_dir/conf/log4j2.xml
crawler_conf=$work_dir/conf/crawler.yaml
lib_path=$work_dir/lib/*

java -server ${jvm_opts} -Dlog4j.configurationFile=file:$logconf_path \
  -cp $lib_path ${main_class} $crawler_conf 1>crawler.out 2>&1