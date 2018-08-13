#!/usr/bin/env bash
zipfile=$1
wget http://storage.killxdcj.com/metadata/human/$zipfile
rm -rf tmp
mkdir -p tmp
unzip $zipfile -d tmp
file=`ls tmp`
mv zipfile tmp/
echo "start index $file"
echo "finsh index $file"
