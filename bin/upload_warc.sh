#!/usr/bin/env sh
cd "`dirname $0`"

curl -X POST -F "sourcebytes=@$1;type=application/octet-stream" -F "flatfile=true" -F "bulk=true" -o $1.elastic http://127.0.0.1:8500/yacy/grid/parser/parser.json
curl -s -XPOST http://elastic:changeme@localhost:9200/web/index/_bulk --data-binary "@$1.elastic"
echo
