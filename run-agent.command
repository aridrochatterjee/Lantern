#!/usr/bin/env bash
set -euo pipefail
rm -rf out
mkdir -p out
javac -d out $(find src/main/java -name '*.java')
exec java -cp out com.lantern.agent.server.LanternAgent "$@"
