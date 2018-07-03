#!/usr/bin/env bash

work_dir=$(cd "$(dirname "$0")";cd ..;pwd)
cd $work_dir

export JAVA_HOME=real_java_home
export PATH=$PATH:$JAVA_HOME/bin
export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar

main_class=com.killxdcj.aiyawocao.meta.proxy.MetaProxyMain
jvm_opts="-Xmx60m -Xms60m -XX:+PrintGCDateStamps -XX:+PrintGCDetails -Xloggc:$work_dir/gc.log"
logconf_path=$work_dir/conf/log4j2.xml
proxy_conf=$work_dir/conf/meta-proxy.yaml
lib_path=$work_dir/lib/meta-proxy-1.1-SNAPSHOT.jar

java -server ${jvm_opts} -Dlog4j.configurationFile=file:$logconf_path \
  -cp $lib_path ${main_class} $proxy_conf 1>proxy.out 2>&1
