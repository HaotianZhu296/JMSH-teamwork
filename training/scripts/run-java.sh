#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi
./scripts/mvn.sh -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java_command=java
if [[ -n "${JAVA_HOME:-}" ]]; then java_command="$JAVA_HOME/bin/java"; fi
exec "$java_command" -Xmx4g -Djava.awt.headless=true \
  -Dai.djl.pytorch.num_threads=2 -Dai.djl.pytorch.num_interop_threads=1 \
  -cp "target/classes:$(cat target/classpath.txt)" "$@"
