#!/bin/sh
set -eu
# Use the installed Java 21 on macOS; Linux/CI uses its configured JAVA_HOME.
if [ "$(uname -s)" = Darwin ]; then
  JAVA_HOME=$(/usr/libexec/java_home -v 21)
  export JAVA_HOME
fi
exec mvn "$@"
