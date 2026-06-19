# PurpleRTP

A network-aware random teleport plugin for Spigot 1.21+ with pre-generated location pools and region selection menus.

## Building

```bash
mvn clean package
```

The built jar ends up in `target/PurpleRTP-2.1.0.jar`. GitHub Actions also builds it automatically on every push.

## Features

- Pre-generates safe locations into a pool so teleports are instant
- Countdown with movement-cancel protection
- Per-world radius, cooldown, and center settings
- **Region menus** — overworld shows a NA/EU picker; nether & end teleport directly (no region picker needed)
- Network config block for BungeeCord proxy IPs/ports
- `/rtpadmin reload | clearcooldown | forcertp | pool`

## Config overview

| Section | Purpose |
|---|---|
| `WORLD-SETTINGS` | Bukkit world radius/cooldown used by the pool generator |
| `SERVER-SETTINGS` | Per-BungeeCord-server radius/world for network routing |
| `NETWORK.REGIONS` | Proxy IPs and which server names belong to each region |
| `RTP-MENU.BUTTONS.<KEY>.ENABLED-REGION` | `true` → opens region sub-menu; `false` → teleports directly |
| `REGION-MENUS` | Sub-menu layout for overworld/nether/end region pickers |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `purplertp.use` | true | Use `/rtp` |
| `purplertp.admin` | op | Use `/rtpadmin` |
| `purplertp.bypass.cooldown` | op | Skip cooldown |
