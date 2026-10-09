# ViaFabricPlus Bedrock

Fork of [ViaFabricPlus-Bedrock](https://github.com/ViaVersionAddons/viafabricplus-bedrock) that makes the Java client
play like the Bedrock client: it moves, builds, fights and sends packets the way Bedrock does, so servers with strict
anticheats like The Hive treat it like a Bedrock player, and draws servers' custom content like Bedrock.

*Vibecoded, while you may use this if you please, be aware it could have horrible optimization issues and such bugs*

## Usage

1. Install [ViaFabricPlus](https://modrinth.com/mod/viafabricplus) 5.1.0 or newer and this addon.
2. Open the ViaFabricPlus screen and select the Bedrock version.
3. For online mode servers, Realms, and friends' worlds, open the settings, switch to the `Bedrock` tab and click
   `Account for Bedrock Edition`. Your browser opens for the Microsoft login, and the entry then shows the name of the
   account that is signed in.

Log in once. The same account is used for every online mode Bedrock
server. `Bedrock Realms` and `Bedrock Friends` in the ViaFabricPlus screen stay disabled until an account is set.

### Accounts

`Bedrock Accounts`, next to the ViaFabricPlus button, keeps several Microsoft accounts and switches between them.
Sign-ins use the phone sign-in flow Bedrock uses. `Create account` walks through making a new Microsoft account, for
testing.

### Friends and worlds

Open `Bedrock Friends` to see your Xbox friends and their online status. The `Requests` tab lets you accept, decline,
or cancel friend requests. Use `Find players` to search by Gamertag and send a request. Select a player and click
`Profile` to see their Xbox details, or `Remove friend` to remove them.

The `Worlds` tab lists joinable worlds hosted by Xbox friends. Select a world and click `Join world`. You can also
select a friend with a joinable world from the `Friends` tab. The screen checks the world's protocol before joining.
Use `Refresh` to update the lists.

To join a NetherNet server directly, select the Bedrock version and enter one of these address forms:

| Address | Signaling service |
| --- | --- |
| `nethernet://host[:port]` | HTTP, default port 19132 |
| `nethernet-lan://host[:port]` | LAN discovery, default port 7551 |
| `nethernet-xbox://network-id` | Xbox WebSocket |
| `nethernet-xbox-json-rpc://network-id` | Xbox JSON-RPC |

Xbox signaling needs a Bedrock account. HTTP and LAN signaling can connect without one if the server permits it.

### Settings

The `Bedrock` settings tab also has:

- `Replace default port in server list`: adds Bedrock's default port to addresses without one.
- `Record packets to bedrock-captures`: writes the Bedrock packets of each connection to a file in `bedrock-captures`,
  for development.

## What this fork changes

### Playing like Bedrock

- **Movement:** Bedrock's friction, soul sand, honey, slime bounces, cobwebs, ladders and vines, fluids, swimming,
  levitation, collision order and floating point limits.
- **Sprinting:** sprint starts and stops are sent like Bedrock, sprinting in water works, and walking into a wall
  sideways or moving diagonally keeps the sprint.
- **Input:** the velocity, interact rotation and jump flags are sent like Bedrock, and the move vector is slowed while
  using an item.
- **Knockback:** the server's motion, attributes and entity data are applied after as many moves as on Bedrock, and
  latency checks are answered at the same time, so knockback lines up with the server.
- **Combat:** attacking slows the attacker like Bedrock, and the reach is Bedrock's: 3 blocks to entities, 5 in creative.
- **Building:** holding use builds lines of blocks with Bedrock's delays. Placements, buckets, eating and block
  predictions work like Bedrock, and looking down past an edge places against the block below (reach-around). Blocks
  are reached from 5.7 blocks away, 12 in creative.
- **Servers with client authoritative inventories and breaking**, like The Hive: item moves are sent as item stack
  requests and broken blocks are reported with the tool's wear.
- **Dimension changes** go through Bedrock's loading screen with its timing.
- **Collision shapes:** blocks shaped differently on Bedrock, like ladders, chests and lanterns, get Bedrock's shape.

### Servers' custom content

- **Custom blocks** are drawn with their own models and textures, and get their collision, light, waterlogging and
  items.
- **Custom entities**, like The Hive's NPCs, are drawn with their Bedrock models and play their animations.
- **Entity name tags** show every line, at the entity's size.
- **Player skins:** Bedrock players get their skins and capes, and 4D skins get their own shape.
- **Forms** show buttons with images in a grid, like Bedrock.
- **Glyphs**, the icons servers put in text, are drawn at their real size.
- **The Hive's sidebar** is drawn in the top right corner like its UI pack.
- **Faster joins:** the models of a server's blocks are kept, so later joins and transfers load the packs once.

### Fixes

ViaBedrock disconnects and glitches fixed along the way: dimension changes and respawns, changing the mined face,
flickering blocks, resource pack downloads, repeated sub chunks, unknown entity links, containers of entities and ender
chests, dimension heights, rain and thunder, and the items of the blocks servers define.

## API for other mods

Other mods can turn each of these features on and off and change the values they use, like the reach distances. See
[docs/API.md](docs/API.md).

## Gradle

The upstream mod is published to the ViaVersion repository as `com.viaversion:viafabricplus-bedrock`. It doesn't
include this fork's changes; to build this fork, run `./gradlew build`.

```kotlin
repositories {
    maven("https://repo.viaversion.com")
}

dependencies {
    // Replace it with latest release
    runtimeOnly("com.viaversion:viafabricplus-bedrock:x.x.x")
}
```

## Links

- ViaFabricPlus: https://github.com/ViaVersion/ViaFabricPlus
- ViaBedrock: https://github.com/RaphiMC/ViaBedrock

## Contact

- Issues: https://github.com/florianreuth/viafabricplus-bedrock/issues
- Discord: https://florianreuth.de/discord
