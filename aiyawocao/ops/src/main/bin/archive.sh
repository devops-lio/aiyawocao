#!/usr/bin/env bash
datex=`date --date='1 day ago' '+%Y%m%d'`
echo `date`"archive job for "$datex

# echo `date`"compress original metadata"
# cd /data/aiyawocao/metadata/original
# zip -rq $datex".zip" $datex
# echo `date`"original metadata compressed"
#rm -rf $datex

echo `date`"index human metadata"
cd /opt/index
rm -rf tmp
mkdir -p tmp
for name in `ls /data/rsync/btproxy/metadata/human | grep $datex | grep zip`
do
	echo `date`"uncompress "$name
	unzip -q /data/rsync/btproxy/metadata/human/$name -d tmp/
done
echo `date`"start index human metadata"
/usr/lib/jdk1.8.0_172/bin/java -Xmx200m -Xms100m -cp ./lib/ops-1.1-SNAPSHOT.jar com.killxdcj.aiyawocao.ops.ESUtils index -e es.host:9620 -p tmp/ -i metadata-v4 -t metadata-v4 -b 500
echo `date`"finished "$datex

# rsyncd 
# cd /opt/index
# echo `date`"do rsync"
# mv /data/aiyawocao/metadata/human/*.zip /data/rsync/metadata/human/
# mv /data/aiyawocao/metadata/original/*.zip /data/rsync/metadata/original/
# rsync -avz /data/rsync/ xdcj@storage.host::btproxy --password-file ./rsync/rsyncd.secrets
# echo `date`"rsync successd"
