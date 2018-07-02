#!/usr/bin/env bash

work_dir=$(cd "$(dirname "$0")";cd ..;pwd)
cd $work_dir

export JAVA_HOME=real_java_home
export PATH=$PATH:$JAVA_HOME/bin
export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar

jvm_opts="-Xmx50m -Xms50m -XX:+PrintGCDateStamps -XX:+PrintGCDetails -Xloggc:$work_dir/gc.log"
logconf_path=$work_dir/conf/log4j2.xml
meta_centre_conf=$work_dir/conf/meta-centre.yaml
lib_path=$work_dir/lib/*

java -server -jar ${jvm_opts} -Dlog4j.configurationFile=file:$logconf_path \
    -Dconf=$meta_centre_conf -Dserver.port=10241 $lib_path 1>meta-centre.out 2>&1