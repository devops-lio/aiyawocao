#!/usr/bin/env bash
dates=`ls /data/metadata/archive/human/ | awk -F '.' '{print $2}' | sort | uniq`
for date in $dates
do
    unalias rm
    echo `date`": reindex start "$date
    datedir="metadata."$date
    mkdir -p $datedir
    for file in `ls /data/metadata/archive/human/ | grep $date`
    do
        echo `date`": uncompress start "$file
        unzip -q /data/metadata/archive/human/$file -d $datedir
        echo `date`": uncompress finished "$file
    done
    oldnum=`cat $datedir/* | wc -l`
    /usr/lib/jdk1.8.0_172/bin/java -Xms1024m -Xmx1024m -cp ./lib/ops-1.1-SNAPSHOT.jar com.killxdcj.aiyawocao.ops.ESReIndexUtils index -e es.host:9620 -p $datedir -i metadata-v2 -t metadata-v2 -b 500
    echo `date`": reindex finished "$date
    echo `date`": zip metadata start "$date
    rm -rf $datedir
    mkdir -p $datedir
    mv logs/metadata/* $datedir
    newnum=`cat $datedir/* | wc -l`
    echo `date`": $date oldnum:$oldnum newnum:$newnum"
    zip -q -r "/data/metadata/archive-new/metadata."$date"-"$newnum".zip" $datedir
    echo `date`": zip metadata finished "$date
    rm -rf $datedir
done
