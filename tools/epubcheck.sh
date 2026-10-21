#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .build
mvn -B -ntp -f tools/epubcheck-pom.xml dependency:build-classpath \
  -Dmdep.outputFile=target/epubcheck-cp.txt > .build/epubcheck-deps.log
epubcheck_classpath=$(cat tools/target/epubcheck-cp.txt)
report=site/downloads/epubcheck.txt
if "${JAVA_BIN:-java}" -cp "$epubcheck_classpath" com.adobe.epubcheck.tool.Checker \
  --failonwarnings site/downloads/netty-book-reader.epub > "$report" 2>&1; then
  cat "$report"
else
  cat "$report" >&2
  exit 1
fi
python3 -c 'import hashlib; print("Validated EPUB SHA-256:", hashlib.sha256(open("site/downloads/netty-book-reader.epub", "rb").read()).hexdigest())' >> "$report"
