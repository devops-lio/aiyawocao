#!/usr/bin/env bash
cd ./aiyawocao
mvn clean install -Dmaven.test.skip=true
cd ../

rm -rf ./skrbt_deploy
mkdir -p ./skrbt_deploy/bin
mkdir -p ./skrbt_deploy/service
mkdir -p ./skrbt_deploy/lib
mkdir -p ./skrbt_deploy/config

cp ./aiyawocao/aiyawocao-web/src/main/bin/* ./skrbt_deploy/bin/
cp ./aiyawocao/aiyawocao-web/src/main/config/* ./skrbt_deploy/config/
cp ./aiyawocao/aiyawocao-web/target/aiyawocao-web-1.1-SNAPSHOT.jar ./skrbt_deploy/lib

java_home=$JAVA_HOME
cat ./aiyawocao/aiyawocao-web/src/main/bin/start_skrbt.sh | sed s:real_java_home:$java_home:g > ./skrbt_deploy/bin/start_skrbt.sh

start_skrbt_bash=$(cd "$(dirname "$0")";cd ..;pwd)/skrbt_deploy/bin/start_skrbt.sh
stop_skrbt_bash=$(cd "$(dirname "$0")";cd ..;pwd)/skrbt_deploy/bin/stop_skrbt.sh
cat ./aiyawocao/aiyawocao-web/src/main/service/skrbt.service | sed s:start_bash_path:$start_skrbt_bash:g | sed s:stop_bash_path:$stop_skrbt_bash:g > ./skrbt_deploy/service/skrbt.service
