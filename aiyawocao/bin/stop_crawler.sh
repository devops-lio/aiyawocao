#!/usr/bin/env bash
ps -ef | grep MetaCrawlerMain | grep -v grep | awk '{print $2}' | xargs -I{} kill -s 15 {}