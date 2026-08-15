# Keeping discord-mcp running

The reaction-role verify gate only works while the process is on the Discord gateway.
Started by hand (`./run.sh`), it dies with the shell — and a member who reacts then gets
no role, silently. Pick one of these.

## macOS (launchd)

```sh
cp deploy/com.numo.discord-mcp.plist ~/Library/LaunchAgents/
launchctl load -w ~/Library/LaunchAgents/com.numo.discord-mcp.plist
```

Starts at login, restarts on crash. Logs to `~/Library/Logs/discord-mcp.log`.

```sh
launchctl list | grep discord-mcp                  # status (pid, last exit code)
launchctl kickstart -k gui/$(id -u)/com.numo.discord-mcp   # restart after a rebuild
launchctl unload -w ~/Library/LaunchAgents/com.numo.discord-mcp.plist  # uninstall
```

Kill any hand-started instance first, or port 8085 will already be taken.

The plist hardcodes absolute paths; update them if the repo moves.

## AWS (production)

See [aws.md](aws.md). A laptop is fine for development; a public server's front door
should not depend on a lid staying open.

## Docker

```sh
docker compose up -d
```

`docker-compose.yml` passes the `VERIFY_*` vars through from `.env` and sets
`restart: unless-stopped`.
