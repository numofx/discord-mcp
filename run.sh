#!/usr/bin/env bash
# Launch discord-mcp in HTTP mode on localhost:8085.
# Reads DISCORD_TOKEN / DISCORD_GUILD_ID from .env next to this script.
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "error: .env not found. Create it with:" >&2
  echo "  DISCORD_TOKEN=..." >&2
  echo "  DISCORD_GUILD_ID=..." >&2
  exit 1
fi

set -a
source .env
set +a

if [[ -z "${DISCORD_TOKEN:-}" ]]; then
  echo "error: DISCORD_TOKEN is empty in .env" >&2
  exit 1
fi

JAR=$(ls -t target/discord-mcp-*.jar 2>/dev/null | grep -v sources | head -1 || true)
if [[ -z "$JAR" ]]; then
  echo "error: no jar in target/. Run: mvn clean package" >&2
  exit 1
fi

# openjdk@21 is keg-only on Homebrew, so bare `java` is the macOS stub.
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
  echo "error: no java at $JAVA_HOME/bin/java (brew install openjdk@21)" >&2
  exit 1
fi

export SPRING_PROFILES_ACTIVE=http
echo "starting $JAR on http://localhost:8085/mcp"
exec "$JAVA_HOME/bin/java" -jar "$JAR"
