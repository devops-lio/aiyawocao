#!/usr/bin/env bash
cd ./aiyawocao
mvn clean install -Dmaven.test.skip=true
cd ../

rm -rf ./metadata_service_deploy
mkdir -p ./metadata_service_deploy/bin
mkdir -p ./metadata_service_deploy/service
mkdir -p ./metadata_service_deploy/lib
mkdir -p ./metadata_service_deploy/conf

cp ./aiyawocao/metadata-service-server/src/main/bin/* ./metadata_service_deploy/bin/
cp ./aiyawocao/metadata-service-server/src/main/conf/* ./metadata_service_deploy/conf/
cp ./aiyawocao/metadata-service-server/target/metadata-service-server-1.1-SNAPSHOT.jar ./metadata_service_deploy/lib

java_home=$JAVA_HOME
cat ./aiyawocao/metadata-service-server/src/main/bin/start_metadata_service.sh | sed s:real_java_home:$java_home:g > ./metadata_service_deploy/bin/start_metadata_service.sh

start_metadata_service_bash=$(cd "$(dirname "$0")";pwd)/metadata_service_deploy/bin/start_metadata_service.sh
stop_metadata_service_bash=$(cd "$(dirname "$0")";pwd)/metadata_service_deploy/bin/stop_metadata_service.sh
cat ./aiyawocao/metadata-service-server/src/main/service/metadata-service.service | sed s:start_bash_path:$start_metadata_service_bash:g | sed s:stop_bash_path:$stop_metadata_service_bash:g > ./metadata_service_deploy/service/metadata-service.service

