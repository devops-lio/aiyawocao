#!/usr/bin/env bash
work_dir=$(cd "$(dirname "$0")";cd ..;pwd)
service_path=$work_dir/service/meta-centre.service
systemctl enable $service_path
