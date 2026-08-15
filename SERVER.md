# Numo Discord — server configuration

How the Numo community server is wired, and the non-obvious decisions behind it.
None of this lives in code: roles, permission overwrites, AutoMod rules and Community
settings live in Discord itself. This file is the record.

Guild: **Numo** — `1538170660180729927`

## Roles

| Role | ID | Purpose |
|---|---|---|
| `numo-mcp` | `1538177919497674785` | Managed integration role for the bot. Created by Discord on invite, cannot be deleted while the bot is in the server. Carries the bot's permissions. |
| `Numo Team` | `1538183948675453050` | Human-facing team label. Permissions `0` — grants nothing on its own; access comes from channel overwrites. |
| `Verified` | `1538192584697061458` | Granted by the reaction gate. Permissions `0`; gates channel visibility via overwrites. |

Hierarchy is `numo-mcp` > `Numo Team` > `Verified` > `@everyone`. **The bot can only manage
roles below its own**, so `numo-mcp` must stay at the top or role assignment breaks.

## Channels

"Gated" below means `@everyone` is denied View Channel and `Verified` is allowed — the
channel only appears after passing the verify gate.

### Uncategorized

| Channel | ID | Notes |
|---|---|---|
| `#verify-here` | `1538187413652774912` | The gate. Community rules channel. Public, read-only, reactions allowed. |
| `#mod-updates` | `1538188750910398575` | Community updates channel. Gated, team-only. |
| `The Stage` | `1538211558017212416` | Gated. `Numo Team` may speak, `Verified` may attend. |
| `Total Members: N` | `1538211822220480538` | Live counter, renamed on a schedule. Gated; `@everyone` denied View + Connect. |

### Welcome (public — visible before verifying)

| Channel | ID | Notes |
|---|---|---|
| `#🔗│info-and-links` | `1538187520301334528` | Read-only for `@everyone`, writable by `Numo Team`. |
| `#❓│faq` | `1538187541453479987` | Same as above. |

### Updates / Community / Developers (all gated)

| Channel | ID | Notes |
|---|---|---|
| `#📣│announcements` | `1538204723537117225` | Team-only posting. |
| `#🔔│socials` | `1538204746786021468` | Team-only posting. |
| `#💬│general` | `1538170661308989482` | |
| `#🔥│gm` | `1538204771364773979` | |
| `#💡│feedback` | `1538204802838827008` | |
| `#🛠️│api-support` | `1538204842047045795` | Integration help. |

Category IDs: Welcome `1538187481881645096`, Updates `1538204384670781491`,
Community `1538204404086472804`, Developers `1538204425020112896`.

The Stage and the counter both carry an explicit `numo-mcp` allow for View + Connect. That
is not decoration — see the voice-channel gotcha below.

## The verification gate

`#verify-here` holds a rules post. Reacting with the custom `:Numo:` emoji
(`1538173850108559440`) grants `Verified`, which reveals the gated channels.

Custom emoji are matched by *name*, so `VERIFY_EMOJI=Numo` is enough. Changing the gate
emoji does not retroactively grant anyone who already reacted with the old one — the
listener fires on the reaction event, not on the reaction's presence.

Implemented by [`ReactionRoleListener`](src/main/java/dev/saseq/listeners/ReactionRoleListener.java),
configured through `.env` (see `.env.example`):

```
VERIFY_MESSAGE_ID=1538192621040828516
VERIFY_EMOJI=Numo   # custom emoji, matched by name
VERIFY_ROLE_ID=1538192584697061458
VERIFY_REMOVE_ON_UNREACT=false
```

Requires the `GUILD_MESSAGE_REACTIONS` intent — added in `DiscordMcpConfig`. Without it the
events never arrive and the gate fails silently.

`VERIFY_REMOVE_ON_UNREACT` is **false** deliberately: an accidental un-react should not
silently lock a member out of the server.

**The gate only works while the process is running.** See [`deploy/README.md`](deploy/README.md).
A dead process means members react and get nothing, with no error anywhere they can see.

If the rules post is ever reposted, `VERIFY_MESSAGE_ID` must be updated to the new message.

## Support tickets

`#🆘│help-zone` (`1538214950550380544`, under Support `1538214663655784520`) holds a panel
message with an **Open ticket** button. Clicking it creates a private channel under
**Created Tickets** (`1538214701543202846`) visible only to its author and `Numo Team`,
with Claim / Close / Reopen / Delete controls.

Implemented by [`TicketListener`](src/main/java/dev/saseq/listeners/TicketListener.java),
configured through `.env`:

```
TICKETS_CATEGORY_ID=1538214701543202846
TICKET_STAFF_ROLE_ID=1538183948675453050
TICKET_LOG_CHANNEL_ID=1538188750910398575   # #mod-updates
TICKET_LOG_PING_STAFF=false
TICKET_COUNTER_CHANNEL_ID=1538188750910398575   # defaults to the log channel
```

New tickets are announced in `#mod-updates` with a jump link. This is not cosmetic: ticket
channels do not appear on a member's channel list until added (see the sticky-onboarding
gotcha), so without the announcement a ticket can sit unread with no visual cue. Staff
should also turn on **Follow Category** for Created Tickets in Browse Channels.

Buttons are message components, so they cannot be sent with the plain `send_message` tool —
[`TicketService`](src/main/java/dev/saseq/services/TicketService.java) exposes
`post_ticket_panel` for that. Re-post the panel if the channel is ever cleared.

Design notes:

- **Ticket numbers never repeat.** The high-water mark is persisted in the counter channel's
  topic as `[tickets:N]`, so deleting every ticket no longer restarts numbering. The next
  number is `max(marker, highest open ticket channel) + 1` — the channel scan is kept as a
  floor so a wiped marker cannot reissue a live ticket's number. Leave `[tickets:N]` in
  place when editing that channel's topic. Writes are async and unsynchronised, so two
  tickets opened in the same instant could still collide.
- **One open ticket per member**, or a public button becomes a channel-spam vector.
- **Close hides the ticket from its author but keeps it for staff.** Deletion is a separate,
  deliberate button.
- The panel message is pinned inside each ticket. `PIN_MESSAGES` is a separate permission
  and is *not* implied by `Manage Messages` — Discord split them. If it is ever revoked the
  ticket still works, unpinned, and the skip is logged rather than raised.

## Member counter

[`MemberCountUpdater`](src/main/java/dev/saseq/schedulers/MemberCountUpdater.java) renames the
counter channel on a schedule:

```
MEMBER_COUNT_CHANNEL_ID=1538211822220480538
MEMBER_COUNT_FORMAT=Total Members: {count}
MEMBER_COUNT_EXCLUDE_BOTS=false
MEMBER_COUNT_INTERVAL_MS=600000
```

**It polls rather than reacting to join/leave events on purpose.** Discord rate-limits channel
renames to roughly two per ten minutes per channel, so an event-driven counter exhausts the
limit and then silently stops updating. The rename is also skipped whenever the name already
matches, so a quiet server spends no rate limit at all.

Counting bots is the default so the number agrees with the count Discord itself shows. Turn
`MEMBER_COUNT_EXCLUDE_BOTS` on for humans only, and relabel the channel to match.

This relies on the explicit `numo-mcp` View + Connect allow on that channel — without it the
bot cannot see the channel to rename it.

## Server settings

- Community: enabled (`COMMUNITY`, `NEWS`)
- Rules channel: `#verify-here` · Updates channel: `#mod-updates`
- Verification level: **Medium** (account ≥5 min old)
- Explicit content filter: scan all members
- Onboarding: **disabled** — it conflicts with a read-only gate (see below)
- `mfa_level: 1` — 2FA required for moderator actions. Bots are exempt, so `numo-mcp` is
  unaffected; human moderators need 2FA on their accounts.

### AutoMod

| Rule | ID |
|---|---|
| Wallet drainer phrases | `1538197138364698666` |
| Mention spam (>5) | `1538197140038492210` |
| Spam content | `1538197141678329876` |
| Profanity and slurs | `1538197143897243799` |

All exempt `Numo Team` and alert to `#mod-updates`.

"seed phrase" and "private key" are deliberately **not** blocked — server rule #2 warns
members about exactly those words, and filtering them would gag the safety advice.

## Gotchas

Seven things that cost time here and will again:

**Grant before you deny.** Denying a permission to `@everyone` also denies it to the bot,
and Discord will not let a bot grant a permission it does not currently hold in that
channel. Always add the allow overwrites first, then apply the `@everyone` deny. Doing it
the other way returns a misleading "Bot lacks permission to manage channel permissions".

**On voice/stage channels this is unrecoverable.** Denying Connect to `@everyone` locks the
bot out of editing *or deleting* that channel — Discord requires Connect to manage a voice
channel's overwrites. It cost two channels here; both had to be deleted by hand and rebuilt.
Give `numo-mcp` an explicit View + Connect allow on every voice channel, first, always.

**A bot can only grant permissions it holds.** Granting `Manage Permissions` through an
overwrite needs Administrator, and `Mute Members` / `Move Members` can't be handed out
because the bot doesn't have them guild-wide. Trim the grant or widen the bot's role.

**`PATCH /guilds` returns 200 for changes it silently drops.** Setting `rules_channel_id`
alone did nothing; it only applied when sent with the full Community bundle (`features`,
both channel IDs, `verification_level`, `explicit_content_filter`). Always read the value
back — a 200 is not proof.

**Onboarding requires a channel where `@everyone` can read *and* send.** With Onboarding on
and `#verify-here` as its only default channel, making that channel read-only is rejected.
Onboarding and a reaction gate are two front doors; pick one.

**Onboarding is sticky.** `GUILD_ONBOARDING_EVER_ENABLED` never clears, so members keep the
opt-in channel-list UX ("This channel is not on your channel list") even with Onboarding
disabled. `default_channel_ids` controls what's on the list by default — and it rejects any
channel `@everyone` can't see, so gated channels can never be defaults. Members add those
through Browse Channels.

**Syncing a channel to its category wipes its overwrites.** `#general` was found ungated
because something re-synced it; the `@everyone` View deny was simply gone, with no warning.
After any hand reorganization, re-check what an unverified member can actually see.

## Open items

- The Numo app is owned by the **Numo** Dev Portal team (`1538243799543849010`), but that
  team has one member. Add a second human or the bus factor is unchanged. Team admins can
  regenerate the bot token, which breaks the running bot until `.env` is updated and it is
  restarted.
- `#info-and-links` has no contract addresses.
- `Numo Team` has no Mute/Move Members, so nobody can moderate voice during a stage.
