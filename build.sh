#!/usr/bin/env bash
cd ./aiyawocao
mvn clean install -Dmaven.test.skip=true
cd ../

rm -rf ./crawler_deploy
mkdir -p ./crawler_deploy/bin
mkdir -p ./crawler_deploy/service
mkdir -p ./crawler_deploy/lib
mkdir -p ./crawler_deploy/conf

rm -rf ./proxy_deploy
mkdir -p ./proxy_deploy/bin
mkdir -p ./proxy_deploy/service
mkdir -p ./proxy_deploy/lib
mkdir -p ./proxy_deploy/conf

cp ./aiyawocao/bin/*crawler.sh ./crawler_deploy/bin/
cp ./aiyawocao/bin/install_meta_crawler_service.sh ./crawler_deploy/bin/
cp ./aiyawocao/meta-crawler/target/meta-crawler*.jar ./crawler_deploy/lib/
cp ./aiyawocao/conf/crawler.yaml ./crawler_deploy/conf/
cp ./aiyawocao/conf/log4j2.xml ./crawler_deploy/conf/

cp ./aiyawocao/bin/*proxy.sh ./proxy_deploy/bin/
cp ./aiyawocao/bin/install_meta_proxy_service.sh ./proxy_deploy/bin/
cp ./aiyawocao/meta-proxy/target/meta-proxy*.jar ./proxy_deploy/lib/
cp ./aiyawocao/conf/meta-proxy.yaml ./proxy_deploy/conf/
cp ./aiyawocao/conf/log4j2.xml ./proxy_deploy/conf/

java_home=$JAVA_HOME
cat ./aiyawocao/bin/start_crawler.sh | sed s:real_java_home:$java_home:g > ./crawler_deploy/bin/start_crawler.sh
cat ./aiyawocao/bin/start_meta_proxy.sh | sed s:real_java_home:$java_home:g > ./proxy_deploy/bin/start_meta_proxy.sh

start_crawler_bash=$(cd "$(dirname "$0")";pwd)/crawler_deploy/bin/start_crawler.sh
stop_crawler_bash=$(cd "$(dirname "$0")";pwd)/crawler_deploy/bin/stop_crawler.sh
cat ./aiyawocao/service/meta-crawler.service | sed s:start_bash_path:$start_crawler_bash:g | sed s:stop_bash_path:$stop_crawler_bash:g > ./crawler_deploy/service/meta-crawler.service

meta_proxy_start_bash=$(cd "$(dirname "$0")";pwd)/proxy_deploy/bin/start_meta_proxy.sh
meta_proxy_stop_bash=$(cd "$(dirname "$0")";pwd)/proxy_deploy/bin/stop_meta_proxy.sh
cat ./aiyawocao/service/meta-proxy.service | sed s:start_bash_path:$meta_proxy_start_bash:g | sed s:stop_bash_path:$meta_proxy_stop_bash:g > ./proxy_deploy/service/meta-proxy.service
