# ViaFabricPlus Bedrock API

Other mods can turn each feature of ViaFabricPlus Bedrock on and off, change the values the features use, read the
skins of Bedrock players and add MoLang queries for the animations of the entities servers define.

## Setup

The API is part of this fork, not of the mod published to the ViaVersion repository. Build the jar with
`./gradlew build` and depend on it in `build.gradle.kts`:

```kotlin
dependencies {
    modCompileOnly(files("libs/viafabricplus-bedrock-x.x.x.jar"))
}
```

Players need the fork installed. Mark it as optional in `fabric.mod.json` with `"suggests"` if your mod also works
without it, and only touch the API classes when `FabricLoader.getInstance().isModLoaded("viafabricplus-bedrock")`.

Then add an entrypoint to your `fabric.mod.json`. It's called once the API is ready, before the player can join a
server:

```json
"entrypoints": {
  "viafabricplus-bedrock": ["com.example.MyBedrockAddon"]
}
```

```java
public final class MyBedrockAddon implements ViaFabricPlusBedrockApiEntrypoint {

    @Override
    public void onApiReady(final ViaFabricPlusBedrockApi api) {
        // Java's reach to entities instead of Bedrock's
        api.value(BedrockFeatures.REACH, BedrockFeatures.Reach.SURVIVAL_ENTITY_REACH, Double.class).set(3.0);
        // Draw 4D skins like normal skins
        api.feature(BedrockFeatures.SKIN_GEOMETRY).setEnabled(false);
    }

}
```

The API can also be used at any time later through `ViaFabricPlusBedrockApi.get()`. All classes are in
`com.viaversion.viafabricplus.bedrock.api`.

Changes made through the API last until the game closes, so set them up again in the entrypoint on every start. The
API can be used from any thread.

## Features

A `BedrockFeature` is something the mod does, like drawing player skins. Every feature has an id from
`BedrockFeatures`, and only acts while the client plays on a Bedrock server.

```java
BedrockFeature skins = api.feature(BedrockFeatures.PLAYER_SKINS);
skins.setEnabled(false);
skins.addListener(enabled -> System.out.println("Skins are now " + (enabled ? "on" : "off")));

for (BedrockFeature feature : api.features()) {
    System.out.println(feature.id() + ": " + feature.description());
}
```

`timing()` says when turning a feature on or off takes effect:

| Timing | Takes effect |
| --- | --- |
| `IMMEDIATELY` | Right away, even while connected |
| `NEXT_SPAWN` | For each entity when it next spawns |
| `NEXT_JOIN` | On the next join or server transfer |

Features that change what the client sends, like `item_use` or `inventory_transactions`, make the client look less
like a Bedrock client when they're off, which anticheats can notice.

## Values

A `BedrockValue` is a number or text a feature uses, like a reach distance. Numbers have a range, and setting one
outside it throws an `IllegalArgumentException`. A changed value is used the next time the feature needs it.

```java
BedrockValue<Double> reach = api.value(BedrockFeatures.REACH, BedrockFeatures.Reach.SURVIVAL_BLOCK_REACH, Double.class);
reach.set(4.5);
reach.addListener(value -> System.out.println("Block reach is now " + value));
reach.reset(); // Back to 5.7
```

Values are `Integer`, `Double` or `String`. Asking for a value with the wrong type throws an
`IllegalArgumentException`.

## Skins

`skin(UUID)` gives a Bedrock player's skin as the server sent it: the texture, the cape, the geometry and whether it's
a 4D skin.

```java
api.skin(player.getUUID()).ifPresent(skin -> {
    BufferedImage texture = skin.image();
    boolean fourD = skin.customShape();
});
```

## MoLang queries

The animations of the entities servers define use MoLang queries, like `query.anim_time`. Mods can add queries the
built-in ones lack, or replace built-in ones, for example to make the animations of a server's NPCs react to something.

```java
api.registerMoLangQuery("is_my_mod_loaded", (entity, identifier, partialTicks, arguments) -> 1);
api.registerMoLangQuery("distance_to", (entity, identifier, partialTicks, arguments) ->
    entity.distanceTo(Minecraft.getInstance().player));
```

Queries are called on the render thread every frame an animation uses them. `arguments` are the numbers the
expression passed, like `2` for `query.my_query(2)`.

## Other

`isBedrockConnection()` tells whether the client is connected to a Bedrock server.

## Feature reference

### `auth_input`

Sends movement like Bedrock: the real velocity, the yaw interactions use, start jumping only on jumps, and the move vector slowed while using an item.

- Constant: `BedrockFeatures.AUTH_INPUT`
- On by default: yes
- Takes effect: right away

| Value | Type | Default | Range | What it is |
| --- | --- | --- | --- | --- |
| `item_use_speed` (`BedrockFeatures.AuthInput.ITEM_USE_SPEED`) | Double | `0.1225` | 0.0 to 1.0 | How much the move vector is scaled while using an item, like eating |

### `sprint`

Sprints like Bedrock: sprinting in water, sprint kept while moving diagonally or walking into a wall sideways, and the server's speed changes applied on top of it.

- Constant: `BedrockFeatures.SPRINT`
- On by default: yes
- Takes effect: right away

### `movement_physics`

Moves like Bedrock: its friction, soul sand, honey, ladders, fluids, swimming, levitation, collision order and floating point limits.

- Constant: `BedrockFeatures.MOVEMENT_PHYSICS`
- On by default: yes
- Takes effect: right away

### `packet_delay`

Applies attribute, motion and entity data packets and answers latency checks after as many moves as Bedrock does, so knockback lines up with the server.

- Constant: `BedrockFeatures.PACKET_DELAY`
- On by default: yes
- Takes effect: right away

| Value | Type | Default | Range | What it is |
| --- | --- | --- | --- | --- |
| `attribute_moves` (`BedrockFeatures.PacketDelay.ATTRIBUTE_MOVES`) | Integer | `3` | 0 to 20 | Moves before attribute changes, like speed, take effect |
| `motion_moves` (`BedrockFeatures.PacketDelay.MOTION_MOVES`) | Integer | `2` | 0 to 20 | Moves before the server's motion, like knockback, is applied |
| `entity_data_moves` (`BedrockFeatures.PacketDelay.ENTITY_DATA_MOVES`) | Integer | `2` | 0 to 20 | Moves before the player's own entity data changes take effect |
| `latency_moves` (`BedrockFeatures.PacketDelay.LATENCY_MOVES`) | Integer | `2` | 0 to 20 | Moves before latency checks are answered |

### `knockback_replay`

Replays the moves made since the server sent knockback for an earlier position, like Bedrock's rewind, instead of applying it to the current one.

- Constant: `BedrockFeatures.KNOCKBACK_REPLAY`
- On by default: yes
- Takes effect: right away

| Value | Type | Default | Range | What it is |
| --- | --- | --- | --- | --- |
| `max_replayed_ticks` (`BedrockFeatures.KnockbackReplay.MAX_REPLAYED_TICKS`) | Integer | `20` | 0 to 100 | The most ticks of moves that are replayed |

### `attack_slowdown`

Slows the player when attacking like Bedrock, which doesn't stop sprinting on attacks.

- Constant: `BedrockFeatures.ATTACK_SLOWDOWN`
- On by default: yes
- Takes effect: right away

### `reach`

Uses Bedrock's reach to blocks and entities instead of Java's.

- Constant: `BedrockFeatures.REACH`
- On by default: yes
- Takes effect: right away

| Value | Type | Default | Range | What it is |
| --- | --- | --- | --- | --- |
| `survival_block_reach` (`BedrockFeatures.Reach.SURVIVAL_BLOCK_REACH`) | Double | `5.7` | 0.0 to 64.0 | Block reach outside creative mode with mouse and keyboard, in blocks |
| `creative_block_reach` (`BedrockFeatures.Reach.CREATIVE_BLOCK_REACH`) | Double | `12.0` | 0.0 to 64.0 | Block reach in creative mode, in blocks |
| `survival_entity_reach` (`BedrockFeatures.Reach.SURVIVAL_ENTITY_REACH`) | Double | `3.0` | 0.0 to 64.0 | Entity reach outside creative mode, in blocks |
| `creative_entity_reach` (`BedrockFeatures.Reach.CREATIVE_ENTITY_REACH`) | Double | `5.0` | 0.0 to 64.0 | Entity reach in creative mode, in blocks |

### `reach_around`

Places blocks against the side of the block below when looking down past an edge, like Bedrock's reach-around.

- Constant: `BedrockFeatures.REACH_AROUND`
- On by default: yes
- Takes effect: right away

| Value | Type | Default | Range | What it is |
| --- | --- | --- | --- | --- |
| `min_pitch` (`BedrockFeatures.ReachAround.MIN_PITCH`) | Double | `45.0` | -90.0 to 90.0 | How far down the player has to look, in degrees |

### `building`

Builds lines of blocks while holding the use button with Bedrock's delays.

- Constant: `BedrockFeatures.BUILDING`
- On by default: yes
- Takes effect: right away

### `item_use`

Sends block placing, item use and attacks as Bedrock's item use transactions, with its bucket and eating timing.

- Constant: `BedrockFeatures.ITEM_USE`
- On by default: yes
- Takes effect: right away

| Value | Type | Default | Range | What it is |
| --- | --- | --- | --- | --- |
| `first_eating_event_tick` (`BedrockFeatures.ItemUse.FIRST_EATING_EVENT_TICK`) | Integer | `8` | 0 to 100 | Ticks of eating before the first eating event |
| `eating_event_interval` (`BedrockFeatures.ItemUse.EATING_EVENT_INTERVAL`) | Integer | `4` | 1 to 100 | Ticks between eating events |

### `block_breaking`

Reports broken blocks in the player's input with the tool's wear, like Bedrock with server authoritative block breaking.

- Constant: `BedrockFeatures.BLOCK_BREAKING`
- On by default: yes
- Takes effect: right away

### `block_prediction_hold`

Keeps placed and broken blocks as predicted until the server sends their update, like Bedrock.

- Constant: `BedrockFeatures.BLOCK_PREDICTION_HOLD`
- On by default: yes
- Takes effect: right away

| Value | Type | Default | Range | What it is |
| --- | --- | --- | --- | --- |
| `server_update_timeout` (`BedrockFeatures.BlockPredictionHold.SERVER_UPDATE_TIMEOUT`) | Integer | `1500` | 0 to 60000 | How long predictions wait for the server, in milliseconds |

### `inventory_transactions`

Sends inventory clicks and drops as Bedrock's item stack requests and transactions. Change it while no container is open.

- Constant: `BedrockFeatures.INVENTORY_TRANSACTIONS`
- On by default: yes
- Takes effect: right away

### `dimension_change`

Loads dimension changes like Bedrock: the loading screen, frozen input and the acknowledgement timing. Change it outside of dimension changes.

- Constant: `BedrockFeatures.DIMENSION_CHANGE`
- On by default: yes
- Takes effect: right away

| Value | Type | Default | Range | What it is |
| --- | --- | --- | --- | --- |
| `start_delay_ticks` (`BedrockFeatures.DimensionChange.START_DELAY_TICKS`) | Integer | `2` | 0 to 100 | Ticks before the loading screen shows |
| `min_loading_ticks` (`BedrockFeatures.DimensionChange.MIN_LOADING_TICKS`) | Integer | `2` | 0 to 100 | Ticks the loading screen shows at least |
| `ack_delay_ticks` (`BedrockFeatures.DimensionChange.ACK_DELAY_TICKS`) | Integer | `7` | 0 to 100 | Ticks before the dimension change is acknowledged |
| `timeout_ticks` (`BedrockFeatures.DimensionChange.TIMEOUT_TICKS`) | Integer | `200` | 1 to 6000 | Ticks before loading gives up waiting for chunks |

### `immobile`

Keeps the player still while the server marks them immobile and for the tick after a teleport, like Bedrock.

- Constant: `BedrockFeatures.IMMOBILE`
- On by default: yes
- Takes effect: right away

### `collision_shapes`

Gives blocks whose shape differs on Bedrock, like ladders, chests and lanterns, Bedrock's shape.

- Constant: `BedrockFeatures.COLLISION_SHAPES`
- On by default: yes
- Takes effect: right away

### `custom_blocks`

Draws the blocks servers define with their own models, textures, collision, light and items.

- Constant: `BedrockFeatures.CUSTOM_BLOCKS`
- On by default: yes
- Takes effect: next join

### `block_model_cache`

Keeps the models of servers' blocks, so later joins load the packs once instead of twice.

- Constant: `BedrockFeatures.BLOCK_MODEL_CACHE`
- On by default: yes
- Takes effect: next join

### `custom_entity_models`

Draws the entities servers define with their Bedrock models instead of ViaBedrock's item displays.

- Constant: `BedrockFeatures.CUSTOM_ENTITY_MODELS`
- On by default: yes
- Takes effect: next spawn

### `entity_animations`

Plays the animations of the entities servers define, from their packs.

- Constant: `BedrockFeatures.ENTITY_ANIMATIONS`
- On by default: yes
- Takes effect: right away

### `name_tags`

Draws the names of Bedrock entities like Bedrock: every line, at the entity's size, and the bodies of entities scaled to nothing hidden.

- Constant: `BedrockFeatures.NAME_TAGS`
- On by default: yes
- Takes effect: right away

### `player_skins`

Draws Bedrock players with their skins and capes.

- Constant: `BedrockFeatures.PLAYER_SKINS`
- On by default: yes
- Takes effect: right away

### `skin_geometry`

Draws skins with their own geometry, 4D skins, with that geometry.

- Constant: `BedrockFeatures.SKIN_GEOMETRY`
- On by default: yes
- Takes effect: right away

### `hive_sidebar`

Draws The Hive's sidebar in the top right corner like its UI pack.

- Constant: `BedrockFeatures.HIVE_SIDEBAR`
- On by default: yes
- Takes effect: right away

| Value | Type | Default | Range | What it is |
| --- | --- | --- | --- | --- |
| `title` (`BedrockFeatures.HiveSidebar.TITLE`) | String | `support.playhive.com/ui` |  | The sidebar title that marks the sidebar |

### `forms`

Shows Bedrock's forms like Bedrock: buttons with images in a grid.

- Constant: `BedrockFeatures.FORMS`
- On by default: yes
- Takes effect: right away

### `glyph_sheets`

Loads the packs' glyph sheets, the icons servers put in text.

- Constant: `BedrockFeatures.GLYPH_SHEETS`
- On by default: yes
- Takes effect: next join

### `debug_hud`

Adds Bedrock information to the debug screen.

- Constant: `BedrockFeatures.DEBUG_HUD`
- On by default: yes
- Takes effect: right away

### `default_port`

Adds Bedrock's default port to addresses without a port. The same as the setting.

- Constant: `BedrockFeatures.DEFAULT_PORT`
- On by default: yes
- Takes effect: right away

### `packet_recording`

Records the Bedrock packets of each connection to a file, for development. The same as the setting.

- Constant: `BedrockFeatures.PACKET_RECORDING`
- On by default: no
- Takes effect: next join
