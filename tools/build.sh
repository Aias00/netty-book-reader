#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 tools/reader_build.py
mkdir -p dist
cp site/downloads/netty-book-reader.epub dist/从零开始学Netty.epub
cp site/downloads/lab.zip dist/Netty学习配套资料.zip
