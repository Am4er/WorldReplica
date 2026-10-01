# WorldReplica

[![Build](https://github.com/Am4er/WorldReplica/actions/workflows/build.yml/badge.svg)](https://github.com/Am4er/WorldReplica/actions/workflows/build.yml)

A small Fabric mod for copying builds. Mark an area with a golden axe, save it as a `.schem` file and paste it somewhere else, in the same world or another one.

Files use the Sponge schematic format, the same one WorldEdit uses, so builds move between the two. Older version 2 files from WorldEdit load too.

## Download

Get the latest jar from the [Releases](https://github.com/Am4er/WorldReplica/releases) page and put it in your `mods` folder together with Fabric API and Fabric Language Kotlin.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- Java 25

On a dedicated server only the server needs the mod. For singleplayer, install it in your game.

## Usage

1. Run `/wr select` to get the selection wand.
2. Left click a block to set position 1, right click a block to set position 2.
3. Run `/wr save castle` to save the area to `schematics/castle.schem`.
4. Stand where you want it and run `/wr load castle`.

A paste lands relative to where you stood when you saved, like WorldEdit's `//copy` and `//paste`.

Big saves and pastes run a few chunks per tick, so the server keeps running while they work. Progress shows above the hotbar and `/wr cancel` stops them. A single schematic can hold up to about two billion blocks.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/wr` | | Lists the commands you can use |
| `/wr select` | `worldreplica:command/select` | Gives you the selection wand |
| `/wr save [name]` | `worldreplica:command/save` | Saves your selection |
| `/wr load <name>` | `worldreplica:command/load` | Pastes a schematic at your position |
| `/wr cancel` | `worldreplica:command/cancel` | Stops your running save or load |
| `/wr reload` | `worldreplica:command/reload` | Reloads the config |

Without a permissions mod every command needs op level 2. With LuckPerms or any other mod that supports the Fabric permission API, grant the nodes instead.

## Discord

WorldReplica can post to a Discord channel whenever someone saves a schematic.

1. In Discord, open Server Settings, then Integrations, then Webhooks, and create a webhook for the channel.
2. Put the webhook URL in `config/worldreplica/discord.json`:

   ```json
   {
     "enabled": true,
     "webhookUrl": "https://discord.com/api/webhooks/..."
   }
   ```

3. Run `/wr reload`.

Keep the URL private. Anyone who has it can post in that channel.

## Files

| Path | Contents |
| --- | --- |
| `schematics/` | Saved schematics |
| `config/worldreplica/discord.json` | Discord settings |

## Building

```
./gradlew build
```

The mod jar ends up in `fabric/build/libs/`. The in-game tests run with `./gradlew :fabric:runGameTest`.

The code is split into four Gradle projects:

| Project | Contents |
| --- | --- |
| `api` | Interfaces other mods can use |
| `platform` | Shared code: jobs, schematic reading and writing, the module system |
| `fabric` | The mod itself: commands, the wand, the Discord module |
| `build-logic` | Shared Gradle setup |

## License

All rights reserved. See [LICENSE](LICENSE).
