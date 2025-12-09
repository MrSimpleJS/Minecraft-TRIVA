# TRIVA - Varo/Project Plugin (Paper 1.21)

TRIVA is an event/Varo plugin for Paper 1.21 built for small teams, clear round logic, and strong spectator/admin tooling. It packs border control, supply drops, loot-llama events, team and streamer workflows, anti-cheat mining analytics, moderation, and comfortable spectator features into one package.

## Features
- **Gameflow & Teams:** Team management (`/team`), ready check (`/teamfertig`, `/fertig`), project start/pause, auto save/load of state, scheduler hints.
- **Border & HUD:** Dynamic border shrink, final phase, HUD with border arrow, scoreboard, performance auto-throttle when TPS is low.
- **PvP & Survival:** Combat tag with rejoin protection, combat-logger NPC, death marker, tracker with cooldown, Nether time limit including `/netherreset`.
- **Loot & Events:** Periodic supply drops, optional loot llama with its own loot pool, drop coords command.
- **Streamer Protection:** Coordinate masking and spectate delay, streamer flagging via command.
- **Spectate & GM:** Specteam GUI, AutoCam, fight radar, POI teleports, GM TP menu, safe TP, area freeze, vanish.
- **Moderation & Reports:** `/report`, review GUI, mute/warn, freeze, inspect, staffchat, logs, announcements.
- **AntiCheat Mining Analytics:** Live adjustable thresholds (`/acset`), profile load/save (`/acprofile`), diamond/debris heatmap logic.
- **Whitelist & Codes:** Create/list/delete whitelist codes, optional dummy/NPC debug mode.
- **Stats & Export:** Kill/death stats, mining stats, end-of-project summary export (`/summary`).

## Installation
1. Prepare a Paper 1.21.x server and Java 21.
2. Build the plugin (`mvn package`) or place the ready-made jar into `plugins/`.
3. Start the server once, adjust `config.yml`, restart.
4. Optional: add **ProtocolLib** so debug NPC dummies are available.

## Configuration (excerpt from `config.yml`)
```yaml
border:
  startSize: 4000
  endSize: 100
  shrinkEveryMinutes: 20
  shrinkLerpSeconds: 30
  centerX: 0
  centerZ: 0

project:
  totalMinutes: 180
  teamSpreadRadius: 700

nether:
  limitMinutes: 60

supplydrop:
  enabled: true
  intervalMinutes: 30
  loot: [GOLDEN_APPLE:2, ENDER_PEARL:6, DIAMOND:3, ARROW:32, BREAD:8]
  mega:
    enabled: true
    chance: 0.1
    loot: [NETHERITE_INGOT:1, DIAMOND:8, ENDER_PEARL:8]

tracker:
  cooldownSeconds: 10

lootlama:
  enabled: false
  intervalMinutes: 45
  radius: 1200
  loot: [DIAMOND:2, GOLDEN_APPLE:1, ENDER_PEARL:2]
```
Additional tuning sections: PvP/combat-tag, spectator (AutoCam), fight radar, privacy, AFK kick, HUD, anti-cheat mining, and performance auto-throttle.

## Key Commands (selection)
- **Core:** `/varo`, `/help`, `/fertig`, `/pausevaro`, `/resetvaro`, `/setcenter`, `/who`
- **Teams:** `/team <create|invite|accept>`, `/teamfertig`
- **Events & Loot:** `/supplydrop`, `/dropcoords`, `/lootlama spawn`
- **Spectate/GM:** `/gm`, `/tpmenu`, `/tpsafe`, `/areafreeze`, `/specteam [Team]`, `/autocam`, `/fightradar`, `/poi`, `/poitp`, `/hud`, `/hudset`
- **Moderation:** `/report`, `/review <Player>`, `/mute|/unmute|/warn`, `/vanish`, `/freeze`, `/inspect`, `/rq`, `/sc`
- **Admin & Utility:** `/netherreset`, `/streamer`, `/tracker`, `/lagreport`, `/pregen`, `/schedule`, `/debugshrink`, `/debugprojzeit`, `/acset`, `/acprofile`, `/wlcode|/wllist|/wldel`, `/summary`
- **Debug:** `/debugfaketeams`, `/debugcleardummys`, `/debugspecdummy`, `/debugnpcteams`

## Build & Development
- Uses Maven (`pom.xml`) and Paper API `1.21.1-R0.1-SNAPSHOT`.
- Java 21 set as source/target.
- Build: `mvn clean package` -> jar is placed in `target/`.
- No hard compile-time deps except Paper API; ProtocolLib is used dynamically if present at runtime.

## Notes
Adjust `config.yml` to your event format (border, timings, loot, privacy). Run admin/debug commands only in testing environments.
