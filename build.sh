#!/usr/bin/env bash
rm -rf ./bin
mkdir -p ./bin
mkdir -p ./bin/conf
mkdir -p ./bin/lib
cd ./aiyawocao
mvn clean install -Dmaven.test.skip=true
cd ../
cp ./aiyawocao/bin/* ./bin/
cp ./aiyawocao/conf/* ./bin/conf/
cp ./aiyawocao/meta-crawler/target/meta-crawler*.jar ./bin/lib/
