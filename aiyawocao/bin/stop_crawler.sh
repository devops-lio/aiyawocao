#!/usr/bin/env bash
ps -ef | grep MetaCrawlerMain | awk '{print $2}' | xargs -I{} kill -s 15 {}