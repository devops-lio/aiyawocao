#!/usr/bin/env bash
dates=`ls /data/metadata/archive/human/ | awk -F '.' '{print $2}' | sort | uniq`
for date in $dates
do
    unalias rm
    echo "reindex start "$date
    datedir="metadata."$date
    mkdir -p $datedir
    for file in `ls /data/metadata/archive/human/ | grep $date`
    do
        echo "uncompress start "$file
        unzip -q /data/metadata/archive/human/$file -d $datedir
        echo "uncompress finished "$file
    done
    /usr/lib/jdk1.8.0_172/bin/java -Xms1024m -Xmx1024m -cp ./lib/ops-1.1-SNAPSHOT.jar com.killxdcj.aiyawocao.ops.ESReIndexUtils index -e es.host:9620 -p $datedir -i metadata-v2 -t metadata-v2 -b 500
    echo "reindex finished "$date
    echo "zip metadata start "$date
    rm -rf "metadata."$date"/*"
    mv logs/metadata/* "metadata."$date"/"
    zip -q -r "/data/metadata/archive-new/medata."$date".zip" "metadata."$date
    echo "zip metadata finished "$date
    rm -rf $datedir
done
