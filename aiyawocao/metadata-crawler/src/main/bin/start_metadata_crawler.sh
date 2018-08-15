#!/usr/bin/env bash

work_dir=$(cd "$(dirname "$0")";cd ..;pwd)
cd $work_dir

export JAVA_HOME=real_java_home
export PATH=$PATH:$JAVA_HOME/bin
export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar

main_class=com.killxdcj.aiyawocao.metadata.crawler.MetaCrawlerMain
jvm_opts="-Xmx150m -Xms150m -Xmn50m -XX:SurvivorRatio=4 -XX:+PrintGCDateStamps -XX:+PrintGCDetails -Xloggc:$work_dir/gc.log"
logconf_path=$work_dir/conf/log4j2.xml
crawler_conf=$work_dir/conf/metadata-crawler.yaml
lib_path=$work_dir/lib/metadata-crawler-1.1-SNAPSHOT.jar

ulimit -n 65535
java -server ${jvm_opts} -Dlog4j.configurationFile=file:$logconf_path \
  -cp $lib_path ${main_class} $crawler_conf 1>crawler.out 2>&1
