/*
 * This file is part of ViaFabricPlus Bedrock - https://github.com/florianreuth/viafabricplus-bedrock
 * Copyright (C) 2021-2026 the original authors
 *                         - Florian Reuth <git@florianreuth.de>
 *                         - RK_01/RaphiMC
 * Copyright (C) 2023-2026 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viafabricplus.bedrock.feature;

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.api.BedrockFeature;
import com.viaversion.viafabricplus.bedrock.api.BedrockFeatures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The features and values of the API, see {@link BedrockFeatures}, as the mod checks them.
 */
public final class Features {

    private static final List<FeatureImpl> ALL = new ArrayList<>();

    public static final FeatureImpl AUTH_INPUT = add(new FeatureImpl(BedrockFeatures.AUTH_INPUT, "Sends movement like Bedrock: the real velocity, the yaw interactions use, start jumping only on jumps, and the move vector slowed while using an item.", BedrockFeature.Timing.IMMEDIATELY, true));
    public static final ValueImpl<Double> ITEM_USE_SPEED = AUTH_INPUT.add(new ValueImpl<>(BedrockFeatures.AuthInput.ITEM_USE_SPEED, "How much the move vector is scaled while using an item, like eating", Double.class, 0.1225, 0.0, 1.0));

    public static final FeatureImpl SPRINT = add(new FeatureImpl(BedrockFeatures.SPRINT, "Sprints like Bedrock: sprinting in water, sprint kept while moving diagonally or walking into a wall sideways, and the server's speed changes applied on top of it.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl MOVEMENT_PHYSICS = add(new FeatureImpl(BedrockFeatures.MOVEMENT_PHYSICS, "Moves like Bedrock: its friction, soul sand, honey, ladders, fluids, swimming, levitation, collision order and floating point limits.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl PACKET_DELAY = add(new FeatureImpl(BedrockFeatures.PACKET_DELAY, "Applies attribute, motion and entity data packets and answers latency checks after as many moves as Bedrock does, so knockback lines up with the server.", BedrockFeature.Timing.IMMEDIATELY, true));
    public static final ValueImpl<Integer> ATTRIBUTE_MOVES = PACKET_DELAY.add(new ValueImpl<>(BedrockFeatures.PacketDelay.ATTRIBUTE_MOVES, "Moves before attribute changes, like speed, take effect", Integer.class, 3, 0, 20));
    public static final ValueImpl<Integer> MOTION_MOVES = PACKET_DELAY.add(new ValueImpl<>(BedrockFeatures.PacketDelay.MOTION_MOVES, "Moves before the server's motion, like knockback, is applied", Integer.class, 2, 0, 20));
    public static final ValueImpl<Integer> ENTITY_DATA_MOVES = PACKET_DELAY.add(new ValueImpl<>(BedrockFeatures.PacketDelay.ENTITY_DATA_MOVES, "Moves before the player's own entity data changes take effect", Integer.class, 2, 0, 20));
    public static final ValueImpl<Integer> LATENCY_MOVES = PACKET_DELAY.add(new ValueImpl<>(BedrockFeatures.PacketDelay.LATENCY_MOVES, "Moves before latency checks are answered", Integer.class, 2, 0, 20));

    public static final FeatureImpl KNOCKBACK_REPLAY = add(new FeatureImpl(BedrockFeatures.KNOCKBACK_REPLAY, "Replays the moves made since the server sent knockback for an earlier position, like Bedrock's rewind, instead of applying it to the current one.", BedrockFeature.Timing.IMMEDIATELY, true));
    public static final ValueImpl<Integer> MAX_REPLAYED_TICKS = KNOCKBACK_REPLAY.add(new ValueImpl<>(BedrockFeatures.KnockbackReplay.MAX_REPLAYED_TICKS, "The most ticks of moves that are replayed", Integer.class, 20, 0, 100));

    public static final FeatureImpl ATTACK_SLOWDOWN = add(new FeatureImpl(BedrockFeatures.ATTACK_SLOWDOWN, "Slows the player when attacking like Bedrock, which doesn't stop sprinting on attacks.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl REACH = add(new FeatureImpl(BedrockFeatures.REACH, "Uses Bedrock's reach to blocks and entities instead of Java's.", BedrockFeature.Timing.IMMEDIATELY, true));
    public static final ValueImpl<Double> SURVIVAL_BLOCK_REACH = REACH.add(new ValueImpl<>(BedrockFeatures.Reach.SURVIVAL_BLOCK_REACH, "Block reach outside creative mode with mouse and keyboard, in blocks", Double.class, 5.7, 0.0, 64.0));
    public static final ValueImpl<Double> CREATIVE_BLOCK_REACH = REACH.add(new ValueImpl<>(BedrockFeatures.Reach.CREATIVE_BLOCK_REACH, "Block reach in creative mode, in blocks", Double.class, 12.0, 0.0, 64.0));
    public static final ValueImpl<Double> SURVIVAL_ENTITY_REACH = REACH.add(new ValueImpl<>(BedrockFeatures.Reach.SURVIVAL_ENTITY_REACH, "Entity reach outside creative mode, in blocks", Double.class, 3.0, 0.0, 64.0));
    public static final ValueImpl<Double> CREATIVE_ENTITY_REACH = REACH.add(new ValueImpl<>(BedrockFeatures.Reach.CREATIVE_ENTITY_REACH, "Entity reach in creative mode, in blocks", Double.class, 5.0, 0.0, 64.0));

    public static final FeatureImpl REACH_AROUND = add(new FeatureImpl(BedrockFeatures.REACH_AROUND, "Places blocks against the side of the block below when looking down past an edge, like Bedrock's reach-around.", BedrockFeature.Timing.IMMEDIATELY, true));
    public static final ValueImpl<Double> MIN_PITCH = REACH_AROUND.add(new ValueImpl<>(BedrockFeatures.ReachAround.MIN_PITCH, "How far down the player has to look, in degrees", Double.class, 45.0, -90.0, 90.0));

    public static final FeatureImpl BUILDING = add(new FeatureImpl(BedrockFeatures.BUILDING, "Builds lines of blocks while holding the use button with Bedrock's delays.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl ITEM_USE = add(new FeatureImpl(BedrockFeatures.ITEM_USE, "Sends block placing, item use and attacks as Bedrock's item use transactions, with its bucket and eating timing.", BedrockFeature.Timing.IMMEDIATELY, true));
    public static final ValueImpl<Integer> FIRST_EATING_EVENT_TICK = ITEM_USE.add(new ValueImpl<>(BedrockFeatures.ItemUse.FIRST_EATING_EVENT_TICK, "Ticks of eating before the first eating event", Integer.class, 8, 0, 100));
    public static final ValueImpl<Integer> EATING_EVENT_INTERVAL = ITEM_USE.add(new ValueImpl<>(BedrockFeatures.ItemUse.EATING_EVENT_INTERVAL, "Ticks between eating events", Integer.class, 4, 1, 100));

    public static final FeatureImpl BLOCK_BREAKING = add(new FeatureImpl(BedrockFeatures.BLOCK_BREAKING, "Reports broken blocks in the player's input with the tool's wear, like Bedrock with server authoritative block breaking.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl BLOCK_PREDICTION_HOLD = add(new FeatureImpl(BedrockFeatures.BLOCK_PREDICTION_HOLD, "Keeps placed and broken blocks as predicted until the server sends their update, like Bedrock.", BedrockFeature.Timing.IMMEDIATELY, true));
    public static final ValueImpl<Integer> SERVER_UPDATE_TIMEOUT = BLOCK_PREDICTION_HOLD.add(new ValueImpl<>(BedrockFeatures.BlockPredictionHold.SERVER_UPDATE_TIMEOUT, "How long predictions wait for the server, in milliseconds", Integer.class, 1500, 0, 60000));

    public static final FeatureImpl INVENTORY_TRANSACTIONS = add(new FeatureImpl(BedrockFeatures.INVENTORY_TRANSACTIONS, "Sends inventory clicks and drops as Bedrock's item stack requests and transactions. Change it while no container is open.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl DIMENSION_CHANGE = add(new FeatureImpl(BedrockFeatures.DIMENSION_CHANGE, "Loads dimension changes like Bedrock: the loading screen, frozen input and the acknowledgement timing. Change it outside of dimension changes.", BedrockFeature.Timing.IMMEDIATELY, true));
    public static final ValueImpl<Integer> START_DELAY_TICKS = DIMENSION_CHANGE.add(new ValueImpl<>(BedrockFeatures.DimensionChange.START_DELAY_TICKS, "Ticks before the loading screen shows", Integer.class, 2, 0, 100));
    public static final ValueImpl<Integer> MIN_LOADING_TICKS = DIMENSION_CHANGE.add(new ValueImpl<>(BedrockFeatures.DimensionChange.MIN_LOADING_TICKS, "Ticks the loading screen shows at least", Integer.class, 2, 0, 100));
    public static final ValueImpl<Integer> ACK_DELAY_TICKS = DIMENSION_CHANGE.add(new ValueImpl<>(BedrockFeatures.DimensionChange.ACK_DELAY_TICKS, "Ticks before the dimension change is acknowledged", Integer.class, 7, 0, 100));
    public static final ValueImpl<Integer> TIMEOUT_TICKS = DIMENSION_CHANGE.add(new ValueImpl<>(BedrockFeatures.DimensionChange.TIMEOUT_TICKS, "Ticks before loading gives up waiting for chunks", Integer.class, 200, 1, 6000));

    public static final FeatureImpl IMMOBILE = add(new FeatureImpl(BedrockFeatures.IMMOBILE, "Keeps the player still while the server marks them immobile and for the tick after a teleport, like Bedrock.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl COLLISION_SHAPES = add(new FeatureImpl(BedrockFeatures.COLLISION_SHAPES, "Gives blocks whose shape differs on Bedrock, like ladders, chests and lanterns, Bedrock's shape.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl CUSTOM_BLOCKS = add(new FeatureImpl(BedrockFeatures.CUSTOM_BLOCKS, "Draws the blocks servers define with their own models, textures, collision, light and items.", BedrockFeature.Timing.NEXT_JOIN, true));

    public static final FeatureImpl BLOCK_MODEL_CACHE = add(new FeatureImpl(BedrockFeatures.BLOCK_MODEL_CACHE, "Keeps the models of servers' blocks, so later joins load the packs once instead of twice.", BedrockFeature.Timing.NEXT_JOIN, true));

    public static final FeatureImpl CUSTOM_ENTITY_MODELS = add(new FeatureImpl(BedrockFeatures.CUSTOM_ENTITY_MODELS, "Draws the entities servers define with their Bedrock models instead of ViaBedrock's item displays.", BedrockFeature.Timing.NEXT_SPAWN, true));

    public static final FeatureImpl ENTITY_ANIMATIONS = add(new FeatureImpl(BedrockFeatures.ENTITY_ANIMATIONS, "Plays the animations of the entities servers define, from their packs.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl NAME_TAGS = add(new FeatureImpl(BedrockFeatures.NAME_TAGS, "Draws the names of Bedrock entities like Bedrock: every line, at the entity's size, and the bodies of entities scaled to nothing hidden.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl PLAYER_SKINS = add(new FeatureImpl(BedrockFeatures.PLAYER_SKINS, "Draws Bedrock players with their skins and capes.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl SKIN_GEOMETRY = add(new FeatureImpl(BedrockFeatures.SKIN_GEOMETRY, "Draws skins with their own geometry, 4D skins, with that geometry.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl HIVE_SIDEBAR = add(new FeatureImpl(BedrockFeatures.HIVE_SIDEBAR, "Draws The Hive's sidebar in the top right corner like its UI pack.", BedrockFeature.Timing.IMMEDIATELY, true));
    public static final ValueImpl<String> TITLE = HIVE_SIDEBAR.add(new ValueImpl<>(BedrockFeatures.HiveSidebar.TITLE, "The sidebar title that marks the sidebar", String.class, "support.playhive.com/ui", null, null));

    public static final FeatureImpl FORMS = add(new FeatureImpl(BedrockFeatures.FORMS, "Shows Bedrock's forms like Bedrock: buttons with images in a grid.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl GLYPH_SHEETS = add(new FeatureImpl(BedrockFeatures.GLYPH_SHEETS, "Loads the packs' glyph sheets, the icons servers put in text.", BedrockFeature.Timing.NEXT_JOIN, true));

    public static final FeatureImpl DEBUG_HUD = add(new FeatureImpl(BedrockFeatures.DEBUG_HUD, "Adds Bedrock information to the debug screen.", BedrockFeature.Timing.IMMEDIATELY, true));

    public static final FeatureImpl DEFAULT_PORT = add(new SettingFeatureImpl(BedrockFeatures.DEFAULT_PORT, "Adds Bedrock's default port to addresses without a port. The same as the setting.", BedrockFeature.Timing.IMMEDIATELY, () -> ViaFabricPlusBedrock.impl().settings().replaceDefaultPort()));

    public static final FeatureImpl PACKET_RECORDING = add(new SettingFeatureImpl(BedrockFeatures.PACKET_RECORDING, "Records the Bedrock packets of each connection to a file, for development. The same as the setting.", BedrockFeature.Timing.NEXT_JOIN, () -> ViaFabricPlusBedrock.impl().settings().recordPackets()));
    private Features() {
    }

    static {
        // Java keeps the shapes of block states, which are worked out again with or without Bedrock's
        COLLISION_SHAPES.addListener(enabled -> Minecraft.getInstance().execute(() -> {
            for (final Block block : BuiltInRegistries.BLOCK) {
                for (final BlockState state : block.getStateDefinition().getPossibleStates()) {
                    state.initCache();
                }
            }
        }));
    }

    private static FeatureImpl add(final FeatureImpl feature) {
        ALL.add(feature);
        return feature;
    }

    public static List<FeatureImpl> all() {
        return Collections.unmodifiableList(ALL);
    }

}
