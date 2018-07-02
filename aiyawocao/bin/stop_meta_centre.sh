#!/usr/bin/env bash
curl http://127.0.0.1:10241/admin/shutdown
ps -ef | grep meta-centre-1.1-SNAPSHOT.jar | grep -v grep | awk '{print $2}' | xargs -I{} kill -s 15 {}
