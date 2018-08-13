#!/usr/bin/env bash
datex=`date --date='1 day ago' '+%Y%m%d'`
echo "archive job for "$datex

echo "compress original metadata"
cd /data/aiyawocao/metadata/original
zip -rq $datex".zip" $datex
echo "original metadata compressed"
#rm -rf $datex

echo "index human metadata"
cd /opt/index
rm -rf tmp
mkdir -p tmp
for name in `ls /data/aiyawocao/metadata/human | grep $datex | grep zip`
do
	echo "uncompress "$name
	unzip -q /data/aiyawocao/metadata/human/$name -d tmp/
done
echo "start index human metadata"
/usr/lib/jdk1.8.0_172/bin/java -Xmx200m -Xmn150m -cp ./lib/ops-1.1-SNAPSHOT.jar com.killxdcj.aiyawocao.ops.ESUtils index -e es.host:9620 -p tmp/ -i metadata -t v1 -b 500

# rsyncd 
cd /opt/index
echo "do rsync"
mv /data/aiyawocao/metadata/human/*.zip /data/rsync/metadata/human/
mv /data/aiyawocao/metadata/original/*.zip /data/rsync/metadata/original/
rsync -avz /data/rsync/ xdcj@storage.host::btproxy --password-file ./rsync/rsyncd.secrets
echo "rsync successd"
