#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi
if command -v mvn >/dev/null 2>&1; then exec mvn "$@"; fi
bundled_maven='/Applications/IntelliJ IDEA.app/Contents/plugins/maven-plugin/lib/maven3/bin/mvn'
if [[ -x "$bundled_maven" ]]; then exec "$bundled_maven" "$@"; fi
printf '%s\n' 'Maven was not found. Install Maven and make mvn available on PATH.' >&2
exit 127
