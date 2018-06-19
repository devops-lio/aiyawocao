#!/usr/bin/env bash
rm -rf ./aiyawocao_deploy
mkdir -p ./aiyawocao_deploy/bin
mkdir -p ./aiyawocao_deploy/service
mkdir -p ./aiyawocao_deploy/lib
mkdir -p ./aiyawocao_deploy/conf
cd ./aiyawocao
mvn clean install -Dmaven.test.skip=true
cd ../
cp ./aiyawocao/bin/* ./aiyawocao_deploy/bin/
cp ./aiyawocao/conf/* ./aiyawocao_deploy/conf/
cp ./aiyawocao/meta-crawler/target/meta-crawler*.jar ./aiyawocao_deploy/lib/

start_bash=$(cd "$(dirname "$0")";pwd)/aiyawocao_deploy/bin/start_crawler.sh
stop_bash=$(cd "$(dirname "$0")";pwd)/aiyawocao_deploy/bin/stop_crawler.sh
cat ./aiyawocao/service/meta-crawler.service | sed s:start_bash_path:$start_bash:g | sed s:stop_bash_path:$stop_bash:g > ./aiyawocao_deploy/service/meta-crawler.service
