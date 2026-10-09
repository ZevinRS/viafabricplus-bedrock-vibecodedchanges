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

package com.viaversion.viafabricplus.bedrock.api;

/**
 * The ids of ViaFabricPlus Bedrock's features, and in the nested classes the ids of their values. For example:
 *
 * <pre>{@code
 * ViaFabricPlusBedrockApi api = ViaFabricPlusBedrockApi.get();
 * api.feature(BedrockFeatures.SKIN_GEOMETRY).setEnabled(false);
 * api.value(BedrockFeatures.REACH, BedrockFeatures.Reach.SURVIVAL_ENTITY_REACH, Double.class).set(3.5);
 * }</pre>
 */
public final class BedrockFeatures {

    /**
     * Sends movement like Bedrock: the real velocity, the yaw interactions use, start jumping only on jumps, and the move vector slowed while using an item.
     */
    public static final String AUTH_INPUT = "auth_input";

    /**
     * Sprints like Bedrock: sprinting in water, sprint kept while moving diagonally or walking into a wall sideways, and the server's speed changes applied on top of it.
     */
    public static final String SPRINT = "sprint";

    /**
     * Moves like Bedrock: its friction, soul sand, honey, ladders, fluids, swimming, levitation, collision order and floating point limits.
     */
    public static final String MOVEMENT_PHYSICS = "movement_physics";

    /**
     * Applies attribute, motion and entity data packets and answers latency checks after as many moves as Bedrock does, so knockback lines up with the server.
     */
    public static final String PACKET_DELAY = "packet_delay";

    /**
     * Replays the moves made since the server sent knockback for an earlier position, like Bedrock's rewind, instead of applying it to the current one.
     */
    public static final String KNOCKBACK_REPLAY = "knockback_replay";

    /**
     * Slows the player when attacking like Bedrock, which doesn't stop sprinting on attacks.
     */
    public static final String ATTACK_SLOWDOWN = "attack_slowdown";

    /**
     * Uses Bedrock's reach to blocks and entities instead of Java's.
     */
    public static final String REACH = "reach";

    /**
     * Places blocks against the side of the block below when looking down past an edge, like Bedrock's reach-around.
     */
    public static final String REACH_AROUND = "reach_around";

    /**
     * Builds lines of blocks while holding the use button with Bedrock's delays.
     */
    public static final String BUILDING = "building";

    /**
     * Sends block placing, item use and attacks as Bedrock's item use transactions, with its bucket and eating timing.
     */
    public static final String ITEM_USE = "item_use";

    /**
     * Reports broken blocks in the player's input with the tool's wear, like Bedrock with server authoritative block breaking.
     */
    public static final String BLOCK_BREAKING = "block_breaking";

    /**
     * Keeps placed and broken blocks as predicted until the server sends their update, like Bedrock.
     */
    public static final String BLOCK_PREDICTION_HOLD = "block_prediction_hold";

    /**
     * Sends inventory clicks and drops as Bedrock's item stack requests and transactions. Change it while no container is open.
     */
    public static final String INVENTORY_TRANSACTIONS = "inventory_transactions";

    /**
     * Loads dimension changes like Bedrock: the loading screen, frozen input and the acknowledgement timing. Change it outside of dimension changes.
     */
    public static final String DIMENSION_CHANGE = "dimension_change";

    /**
     * Keeps the player still while the server marks them immobile and for the tick after a teleport, like Bedrock.
     */
    public static final String IMMOBILE = "immobile";

    /**
     * Gives blocks whose shape differs on Bedrock, like ladders, chests and lanterns, Bedrock's shape.
     */
    public static final String COLLISION_SHAPES = "collision_shapes";

    /**
     * Draws the blocks servers define with their own models, textures, collision, light and items.
     */
    public static final String CUSTOM_BLOCKS = "custom_blocks";

    /**
     * Keeps the models of servers' blocks, so later joins load the packs once instead of twice.
     */
    public static final String BLOCK_MODEL_CACHE = "block_model_cache";

    /**
     * Draws the entities servers define with their Bedrock models instead of ViaBedrock's item displays.
     */
    public static final String CUSTOM_ENTITY_MODELS = "custom_entity_models";

    /**
     * Plays the animations of the entities servers define, from their packs.
     */
    public static final String ENTITY_ANIMATIONS = "entity_animations";

    /**
     * Draws the names of Bedrock entities like Bedrock: every line, at the entity's size, and the bodies of entities scaled to nothing hidden.
     */
    public static final String NAME_TAGS = "name_tags";

    /**
     * Draws Bedrock players with their skins and capes.
     */
    public static final String PLAYER_SKINS = "player_skins";

    /**
     * Draws skins with their own geometry, 4D skins, with that geometry.
     */
    public static final String SKIN_GEOMETRY = "skin_geometry";

    /**
     * Draws The Hive's sidebar in the top right corner like its UI pack.
     */
    public static final String HIVE_SIDEBAR = "hive_sidebar";

    /**
     * Shows Bedrock's forms like Bedrock: buttons with images in a grid.
     */
    public static final String FORMS = "forms";

    /**
     * Loads the packs' glyph sheets, the icons servers put in text.
     */
    public static final String GLYPH_SHEETS = "glyph_sheets";

    /**
     * Adds Bedrock information to the debug screen.
     */
    public static final String DEBUG_HUD = "debug_hud";

    /**
     * Adds Bedrock's default port to addresses without a port. The same as the setting.
     */
    public static final String DEFAULT_PORT = "default_port";

    /**
     * Records the Bedrock packets of each connection to a file, for development. The same as the setting.
     */
    public static final String PACKET_RECORDING = "packet_recording";

    /**
     * The values of {@link #AUTH_INPUT}.
     */
    public static final class AuthInput {

        /**
         * How much the move vector is scaled while using an item, like eating. Double, 0.1225 by default, 0.0 to 1.0.
         */
        public static final String ITEM_USE_SPEED = "item_use_speed";

        private AuthInput() {
        }

    }

    /**
     * The values of {@link #PACKET_DELAY}.
     */
    public static final class PacketDelay {

        /**
         * Moves before attribute changes, like speed, take effect. Integer, 3 by default, 0 to 20.
         */
        public static final String ATTRIBUTE_MOVES = "attribute_moves";

        /**
         * Moves before the server's motion, like knockback, is applied. Integer, 2 by default, 0 to 20.
         */
        public static final String MOTION_MOVES = "motion_moves";

        /**
         * Moves before the player's own entity data changes take effect. Integer, 2 by default, 0 to 20.
         */
        public static final String ENTITY_DATA_MOVES = "entity_data_moves";

        /**
         * Moves before latency checks are answered. Integer, 2 by default, 0 to 20.
         */
        public static final String LATENCY_MOVES = "latency_moves";

        private PacketDelay() {
        }

    }

    /**
     * The values of {@link #KNOCKBACK_REPLAY}.
     */
    public static final class KnockbackReplay {

        /**
         * The most ticks of moves that are replayed. Integer, 20 by default, 0 to 100.
         */
        public static final String MAX_REPLAYED_TICKS = "max_replayed_ticks";

        private KnockbackReplay() {
        }

    }

    /**
     * The values of {@link #REACH}.
     */
    public static final class Reach {

        /**
         * Block reach outside creative mode with mouse and keyboard, in blocks. Double, 5.7 by default, 0.0 to 64.0.
         */
        public static final String SURVIVAL_BLOCK_REACH = "survival_block_reach";

        /**
         * Block reach in creative mode, in blocks. Double, 12.0 by default, 0.0 to 64.0.
         */
        public static final String CREATIVE_BLOCK_REACH = "creative_block_reach";

        /**
         * Entity reach outside creative mode, in blocks. Double, 3.0 by default, 0.0 to 64.0.
         */
        public static final String SURVIVAL_ENTITY_REACH = "survival_entity_reach";

        /**
         * Entity reach in creative mode, in blocks. Double, 5.0 by default, 0.0 to 64.0.
         */
        public static final String CREATIVE_ENTITY_REACH = "creative_entity_reach";

        private Reach() {
        }

    }

    /**
     * The values of {@link #REACH_AROUND}.
     */
    public static final class ReachAround {

        /**
         * How far down the player has to look, in degrees. Double, 45.0 by default, -90.0 to 90.0.
         */
        public static final String MIN_PITCH = "min_pitch";

        private ReachAround() {
        }

    }

    /**
     * The values of {@link #ITEM_USE}.
     */
    public static final class ItemUse {

        /**
         * Ticks of eating before the first eating event. Integer, 8 by default, 0 to 100.
         */
        public static final String FIRST_EATING_EVENT_TICK = "first_eating_event_tick";

        /**
         * Ticks between eating events. Integer, 4 by default, 1 to 100.
         */
        public static final String EATING_EVENT_INTERVAL = "eating_event_interval";

        private ItemUse() {
        }

    }

    /**
     * The values of {@link #BLOCK_PREDICTION_HOLD}.
     */
    public static final class BlockPredictionHold {

        /**
         * How long predictions wait for the server, in milliseconds. Integer, 1500 by default, 0 to 60000.
         */
        public static final String SERVER_UPDATE_TIMEOUT = "server_update_timeout";

        private BlockPredictionHold() {
        }

    }

    /**
     * The values of {@link #DIMENSION_CHANGE}.
     */
    public static final class DimensionChange {

        /**
         * Ticks before the loading screen shows. Integer, 2 by default, 0 to 100.
         */
        public static final String START_DELAY_TICKS = "start_delay_ticks";

        /**
         * Ticks the loading screen shows at least. Integer, 2 by default, 0 to 100.
         */
        public static final String MIN_LOADING_TICKS = "min_loading_ticks";

        /**
         * Ticks before the dimension change is acknowledged. Integer, 7 by default, 0 to 100.
         */
        public static final String ACK_DELAY_TICKS = "ack_delay_ticks";

        /**
         * Ticks before loading gives up waiting for chunks. Integer, 200 by default, 1 to 6000.
         */
        public static final String TIMEOUT_TICKS = "timeout_ticks";

        private DimensionChange() {
        }

    }

    /**
     * The values of {@link #HIVE_SIDEBAR}.
     */
    public static final class HiveSidebar {

        /**
         * The sidebar title that marks the sidebar. String, support.playhive.com/ui by default.
         */
        public static final String TITLE = "title";

        private HiveSidebar() {
        }

    }
    private BedrockFeatures() {
    }

}
