# Running the bot on AWS

The verify gate, tickets and member counter only work while this process is on the Discord
gateway. On a laptop that means a closed lid silently breaks the front door of the server —
and because `#help-zone` is gated behind `Verified`, a member who cannot verify also cannot
open a ticket to report it. That is the reason to move it.

## Before you start

**Only one instance may run.** Two copies both hold the gateway connection, so one button
click creates two ticket channels. Stop the laptop one first:

```sh
launchctl unload -w ~/Library/LaunchAgents/com.numo.discord-mcp.plist
```

**Rotate the bot token** once the move is done. It will have existed on two machines. The
app is team-owned, so regenerate it in the Developer Portal → Numo → Bot → Reset Token, put
the new value in the server's `.env` only, and restart.

## The running instance

| | |
|---|---|
| Lightsail instance | `numo-bot-2`, `small_3_0` (2 GB), us-east-1a, $12/mo |
| Static IP | `34.194.252.122` (free while attached) |
| SSH | `ssh numo-bot` — alias in `~/.ssh/config`, key `~/.ssh/numo-bot` |
| Service | `numo-discord-mcp`, enabled at boot |
| Open ports | TCP/22 only |

**Sizing.** 1 GB (`micro_3_0`, $7/mo) is enough — the bot idles around 0.2% CPU and a few
hundred MB. The 2 GB tier here is a leftover from debugging and can be dropped by snapshot
and restore.

**Burst capacity is the real constraint during setup, not RAM.** A fresh Lightsail instance
starts at ~0% CPU burst and accrues over time, including instances restored from a snapshot.
Installing a JDK exhausts it, after which SSH becomes intermittent for a while — the instance
is throttled, not broken (`StatusCheckFailed` stays 0). Wait for `BurstCapacityPercentage`
to recover rather than resizing:

```sh
aws lightsail get-instance-metric-data --region us-east-1 --instance-name numo-bot-2 \
  --metric-name BurstCapacityPercentage --period 300 --unit Percent --statistics Maximum \
  --start-time <iso8601> --end-time <iso8601>
```

Use `rsync --partial` rather than `scp` for the jar; a throttled link drops 50 MB streams
and rsync resumes where it stopped.

**Security group: SSH only.** Do *not* open 8085. The MCP endpoint has no authentication —
anyone who can reach it can create channels, assign roles and read messages. `.env` also
sets `SERVER_ADDRESS=127.0.0.1` so it never binds the public interface even if a rule is
added by mistake.

## Setup

```sh
sudo dnf install -y java-21-amazon-corretto-headless
sudo useradd --system --home /opt/numo-discord-mcp --shell /usr/sbin/nologin numo
sudo mkdir -p /opt/numo-discord-mcp
sudo touch /var/log/numo-discord-mcp.log
sudo chown numo:numo /opt/numo-discord-mcp /var/log/numo-discord-mcp.log
```

Copy `.env` to `/opt/numo-discord-mcp/.env`, then:

```sh
sudo chown numo:numo /opt/numo-discord-mcp/.env
sudo chmod 600 /opt/numo-discord-mcp/.env      # it holds the bot token
```

Add these two lines to the server's copy:

```
SPRING_PROFILES_ACTIVE=http
SERVER_ADDRESS=127.0.0.1
```

> **systemd `EnvironmentFile` is not a shell.** Any value containing spaces must be quoted —
> `MEMBER_COUNT_FORMAT="Total Members: {count}"` — or the service fails to start with a
> confusing parse error. Values without spaces need no quotes.

Install the unit:

```sh
sudo cp deploy/numo-discord-mcp.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now numo-discord-mcp
systemctl status numo-discord-mcp
```

## Deploying changes

```sh
./deploy/deploy-aws.sh numo-bot
```

Builds locally, ships the jar, restarts, and prints the tail of the log.

## Using MCP from Claude Code

Port 8085 is not reachable from the internet by design. Tunnel it:

```sh
ssh -L 8085:localhost:8085 numo-bot
```

Claude Code then talks to `http://localhost:8085/mcp` as before, for as long as the tunnel
is open.

## Checking it

```sh
sudo systemctl status numo-discord-mcp
sudo tail -f /var/log/numo-discord-mcp.log
sudo journalctl -u numo-discord-mcp -n 50
```

A healthy start logs `Login Successful!` then `Started DiscordMcpApplication`.
