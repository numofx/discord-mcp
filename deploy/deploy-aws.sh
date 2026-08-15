#!/usr/bin/env bash
# Build locally and ship the jar to the AWS host.
#
#   ./deploy/deploy-aws.sh numo-bot          # host or ssh alias
#
# Requires: the host is already set up per deploy/aws.md (java, /opt/numo-discord-mcp,
# .env in place, systemd unit installed).
set -euo pipefail

HOST="${1:-}"
if [[ -z "$HOST" ]]; then
  echo "usage: $0 <ssh-host>" >&2
  exit 1
fi

cd "$(dirname "$0")/.."

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
echo "==> building"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1
mvn -q package -DskipTests

JAR=$(ls -t target/discord-mcp-*.jar | grep -v sources | head -1)
echo "==> shipping $JAR"

# Upload beside the live jar, then swap and restart, so a failed transfer cannot leave
# a truncated jar in place.
scp "$JAR" "$HOST:/tmp/discord-mcp.jar.new"
ssh "$HOST" '
  set -euo pipefail
  sudo mv /tmp/discord-mcp.jar.new /opt/numo-discord-mcp/discord-mcp.jar
  sudo chown numo:numo /opt/numo-discord-mcp/discord-mcp.jar
  sudo systemctl restart numo-discord-mcp
  sleep 5
  sudo systemctl is-active numo-discord-mcp
'

echo "==> deployed. Recent log:"
ssh "$HOST" 'sudo tail -5 /var/log/numo-discord-mcp.log'
