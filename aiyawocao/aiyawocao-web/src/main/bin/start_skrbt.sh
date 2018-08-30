#!/usr/bin/env bash

work_dir=$(cd "$(dirname "$0")";cd ..;pwd)
cd $work_dir

export JAVA_HOME=real_java_home
export PATH=$PATH:$JAVA_HOME/bin
export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar

main_class=com.killxdcj.aiyawocao.web.WebMain
jvm_opts="-Xmx200m -Xms200m -XX:+PrintGCDateStamps -XX:+PrintGCDetails -Xloggc:$work_dir/gc.log"
springboot_conf=$work_dir/config/application.properties
lib_path=$work_dir/lib/aiyawocao-web-1.1-SNAPSHOT.jar

java -server ${jvm_opts} -Dspring.config.location=$springboot_conf -jar $lib_path ${main_class} >skrbt.out 2>&1
