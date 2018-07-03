#!/usr/bin/env bash
ps -ef | grep MetaProxyMain | grep -v grep | awk '{print $2}' | xargs -I{} kill -s 15 {}
