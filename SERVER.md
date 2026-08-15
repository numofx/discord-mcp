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

| Channel | ID | Notes |
|---|---|---|
| `#verify-here` | `1538187413652774912` | The gate. Community rules channel. Read-only for `@everyone`, reactions allowed. |
| `#general` | `1538170661308989482` | Gated: `@everyone` denied View, `Verified` + `Numo Team` allowed. |
| `#🔗│info-and-links` | `1538187520301334528` | Read-only for `@everyone`, writable by `Numo Team`. |
| `#❓│faq` | `1538187541453479987` | Same as above. |
| `#mod-updates` | `1538188750910398575` | Community updates channel. Hidden from `@everyone`. |
| `Total Members: 2` | `1538187460830564473` | Voice channel used as a counter. `@everyone` denied Connect. The number is just text — nothing updates it. |
| `The Stage` | `1538191526390141098` | See "Known issues". |

## The verification gate

`#verify-here` holds a rules post. Reacting with 🔥 grants `Verified`, which reveals the
gated channels.

Implemented by [`ReactionRoleListener`](src/main/java/dev/saseq/listeners/ReactionRoleListener.java),
configured through `.env` (see `.env.example`):

```
VERIFY_MESSAGE_ID=1538192621040828516
VERIFY_EMOJI=🔥
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

## Server settings

- Community: enabled (`COMMUNITY`, `NEWS`)
- Rules channel: `#verify-here` · Updates channel: `#mod-updates`
- Verification level: **Medium** (account ≥5 min old)
- Explicit content filter: scan all members
- Onboarding: **disabled** — it conflicts with a read-only gate (see below)
- `mfa_level: 0` — 2FA-for-moderation is still off, and should be on before public invites

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

Four things that cost time here and will again:

**Grant before you deny.** Denying a permission to `@everyone` also denies it to the bot,
and Discord will not let a bot grant a permission it does not currently hold in that
channel. Always add the allow overwrites first, then apply the `@everyone` deny. Doing it
the other way returns a misleading "Bot lacks permission to manage channel permissions".

**On voice/stage channels this is unrecoverable.** Denying Connect to `@everyone` locks the
bot out of editing *or deleting* that channel — Discord requires Connect to manage a voice
channel's overwrites. Only a human can undo it. Always grant the bot's role Connect first.

**`PATCH /guilds` returns 200 for changes it silently drops.** Setting `rules_channel_id`
alone did nothing; it only applied when sent with the full Community bundle (`features`,
both channel IDs, `verification_level`, `explicit_content_filter`). Always read the value
back — a 200 is not proof.

**Onboarding requires a channel where `@everyone` can read *and* send.** With Onboarding on
and `#verify-here` as its only default channel, making that channel read-only is rejected.
Onboarding and a reaction gate are two front doors; pick one.

## Open items

- `The Stage`: the bot denied Connect to `@everyone` before granting itself, so it can no
  longer manage or delete the channel. Fix in the UI — remove the `@everyone` Connect deny,
  or grant `numo-mcp` Connect — then `Verified` can be given access.
- 2FA-for-moderation is off (`mfa_level: 0`).
- The Discord *application* is owned by a personal account, not a Dev Portal Team. If that
  account is lost, so is the bot.
- `#info-and-links` and `#faq` are empty.
- The member counter is static text.
