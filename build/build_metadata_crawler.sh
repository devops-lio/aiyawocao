#!/usr/bin/env bash
cd ./aiyawocao
mvn clean install -Dmaven.test.skip=true
cd ../

rm -rf ./metadata_crawler_deploy
mkdir -p ./metadata_crawler_deploy/bin
mkdir -p ./metadata_crawler_deploy/service
mkdir -p ./metadata_crawler_deploy/lib
mkdir -p ./metadata_crawler_deploy/conf

cp ./aiyawocao/metadata-crawler/src/main/bin/* ./metadata_crawler_deploy/bin/
cp ./aiyawocao/metadata-crawler/src/main/conf/* ./metadata_crawler_deploy/conf/
cp ./aiyawocao/metadata-crawler/target/metadata-crawler-1.1-SNAPSHOT.jar ./metadata_crawler_deploy/lib

java_home=$JAVA_HOME
cat ./aiyawocao/metadata-crawler/src/main/bin/start_metadata_crawler.sh | sed s:real_java_home:$java_home:g > ./metadata_crawler_deploy/bin/start_metadata_crawler.sh

start_metadata_crawler_bash=$(cd "$(dirname "$0")";cd ..;pwd)/metadata_crawler_deploy/bin/start_metadata_crawler.sh
stop_metadata_crawler_bash=$(cd "$(dirname "$0")";cd ..;pwd)/metadata_crawler_deploy/bin/stop_metadata_crawler.sh
cat ./aiyawocao/metadata-crawler/src/main/service/metadata-crawler.service | sed s:start_bash_path:$start_metadata_crawler_bash:g | sed s:stop_bash_path:$stop_metadata_crawler_bash:g > ./metadata_crawler_deploy/service/metadata-crawler.service

