#!/usr/bin/env bash

work_dir=$(cd "$(dirname "$0")";cd ..;pwd)
cd $work_dir

export JAVA_HOME=real_java_home
export PATH=$PATH:$JAVA_HOME/bin
export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar

main_class=com.killxdcj.aiyawocao.metadata.service.server.MetadataServiceServerMain
jvm_opts="-Xmx200m -Xms200m -XX:+PrintGCDateStamps -XX:+PrintGCDetails -Xloggc:$work_dir/gc.log"
logconf_path=$work_dir/conf/log4j2.xml
proxy_conf=$work_dir/conf/metadata-service.yaml
lib_path=$work_dir/lib/metadata-service-server-1.1-SNAPSHOT.jar

java -server ${jvm_opts} -Dlog4j.configurationFile=file:$logconf_path \
  -cp $lib_path ${main_class} -c $proxy_conf 1>metadata_service.out 2>&1
