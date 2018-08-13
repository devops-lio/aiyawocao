#!/usr/bin/env bash
cd ./aiyawocao
mvn clean install -Dmaven.test.skip=true
cd ../

rm -rf ./op_deploy
mkdir -p ./op_deploy/bin
mkdir -p ./op_deploy/lib

cp ./aiyawocao/ops/src/main/bin/* ./op_deploy/bin/
cp ./aiyawocao/ops/target/ops-1.1-SNAPSHOT.jar ./op_deploy/lib
