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
cp ./aiyawocao/meta-centre/target/meta-centre*.jar  ./aiyawocao_deploy/lib/
cp ./aiyawocao/meta-proxy/target/meta-proxy*.jar  ./aiyawocao_deploy/lib/

java_home=$JAVA_HOME
cat ./aiyawocao/bin/start_crawler.sh | sed s:real_java_home:$java_home:g > ./aiyawocao_deploy/bin/start_crawler.sh
cat ./aiyawocao/bin/start_meta_centre.sh | sed s:real_java_home:$java_home:g > ./aiyawocao_deploy/bin/start_meta_centre.sh
cat ./aiyawocao/bin/start_meta_proxy.sh | sed s:real_java_home:$java_home:g > ./aiyawocao_deploy/bin/start_meta_proxy.sh

start_bash=$(cd "$(dirname "$0")";pwd)/aiyawocao_deploy/bin/start_crawler.sh
stop_bash=$(cd "$(dirname "$0")";pwd)/aiyawocao_deploy/bin/stop_crawler.sh
cat ./aiyawocao/service/meta-crawler.service | sed s:start_bash_path:$start_bash:g | sed s:stop_bash_path:$stop_bash:g > ./aiyawocao_deploy/service/meta-crawler.service

meta_centre_start_bash=$(cd "$(dirname "$0")";pwd)/aiyawocao_deploy/bin/start_meta_centre.sh
meta_centre_sstop_bash=$(cd "$(dirname "$0")";pwd)/aiyawocao_deploy/bin/stop_meta_centre.sh
cat ./aiyawocao/service/meta-centre.service | sed s:start_bash_path:$meta_centre_start_bash:g | sed s:stop_bash_path:$meta_centre_stop_bash:g > ./aiyawocao_deploy/service/meta-centre.service

meta_proxy_start_bash=$(cd "$(dirname "$0")";pwd)/aiyawocao_deploy/bin/start_meta_proxy.sh
meta_proxy_stop_bash=$(cd "$(dirname "$0")";pwd)/aiyawocao_deploy/bin/stop_meta_proxy.sh
cat ./aiyawocao/service/meta-proxy.service | sed s:start_bash_path:$meta_centre_start_bash:g | sed s:stop_bash_path:$meta_proxy_stop_bash:g > ./aiyawocao_deploy/service/meta-proxy.service
