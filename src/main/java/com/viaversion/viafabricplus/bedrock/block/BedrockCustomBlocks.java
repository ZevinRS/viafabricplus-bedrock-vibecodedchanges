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

package com.viaversion.viafabricplus.bedrock.block;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.jetbrains.annotations.Nullable;

/**
 * Blocks for the blocks servers define, like The Hive's 749 blocks. Java can't add blocks while connected, so a pool of
 * blank blocks is registered at startup and each block state a server defines gets one of them, with the server's
 * collision, outline, friction, light and break time, see {@link BedrockCustomBlockDefinition}, and a model made from
 * its packs. Full blocks hide the faces next to them and block light like Java's; shaped ones don't.
 * <p>
 * Servers never send these blocks to Java clients, so they don't change anything outside of Bedrock servers.
 */
public final class BedrockCustomBlocks implements ModInitializer {

    public static final String NAMESPACE = "viafabricplus-bedrock";
    public static final int FULL_BLOCKS = 2048;
    public static final int SHAPED_BLOCKS = 2048;

    private static final List<Block> FULL = new ArrayList<>(FULL_BLOCKS);
    private static final List<Block> SHAPED = new ArrayList<>(SHAPED_BLOCKS);
    // The definition of each pool block, replaced as a whole when a server defines its blocks
    private static volatile BedrockCustomBlockDefinition[] fullDefinitions = new BedrockCustomBlockDefinition[FULL_BLOCKS];
    private static volatile BedrockCustomBlockDefinition[] shapedDefinitions = new BedrockCustomBlockDefinition[SHAPED_BLOCKS];

    @Override
    public void onInitialize() {
        for (int i = 0; i < FULL_BLOCKS; i++) {
            final int index = i;
            FULL.add(register("full_" + i, properties -> new BedrockFullBlock(properties, index), BlockBehaviour.Properties.of()));
        }
        for (int i = 0; i < SHAPED_BLOCKS; i++) {
            final int index = i;
            SHAPED.add(register("shaped_" + i, properties -> new BedrockShapedBlock(properties, index), BlockBehaviour.Properties.of().noOcclusion().dynamicShape()));
        }
    }

    private static Block register(final String name, final Function<BlockBehaviour.Properties, Block> factory, final BlockBehaviour.Properties properties) {
        final Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, "bedrock_block/" + name);
        final ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        final Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
        final ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        final BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
        item.registerBlocks(Item.BY_BLOCK, item);
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        return block;
    }

    public static Block fullBlock(final int index) {
        return FULL.get(index);
    }

    public static Block shapedBlock(final int index) {
        return SHAPED.get(index);
    }

    public static @Nullable BedrockCustomBlockDefinition fullDefinition(final int index) {
        return fullDefinitions[index];
    }

    public static @Nullable BedrockCustomBlockDefinition shapedDefinition(final int index) {
        return shapedDefinitions[index];
    }

    /**
     * Replaces the definitions of all pool blocks, when a server defined its blocks.
     */
    public static void setDefinitions(final BedrockCustomBlockDefinition[] full, final BedrockCustomBlockDefinition[] shaped) {
        fullDefinitions = full;
        shapedDefinitions = shaped;
        lightEmissions = null;
        lightFilters = null;
    }

    // ViaBedrock's light emission and light filter of every Java block state, with the pool blocks added
    private static volatile byte[] lightEmissions;
    private static volatile byte[] lightFilters;

    /**
     * ViaBedrock lights chunks itself, with tables of the vanilla block states only.
     *
     * @return ViaBedrock's light emission table with the pool blocks added
     */
    public static byte[] lightEmissions(final byte[] vanillaTable) {
        byte[] table = lightEmissions;
        if (table == null) {
            lightEmissions = table = withPoolBlocks(vanillaTable, true);
        }
        return table;
    }

    /**
     * @return ViaBedrock's light filter table with the pool blocks added
     */
    public static byte[] lightFilters(final byte[] vanillaTable) {
        byte[] table = lightFilters;
        if (table == null) {
            lightFilters = table = withPoolBlocks(vanillaTable, false);
        }
        return table;
    }

    private static byte[] withPoolBlocks(final byte[] vanillaTable, final boolean emission) {
        final byte[] table = new byte[Math.max(vanillaTable.length, Block.BLOCK_STATE_REGISTRY.size())];
        System.arraycopy(vanillaTable, 0, table, 0, vanillaTable.length);
        fill(table, FULL, fullDefinitions, emission);
        fill(table, SHAPED, shapedDefinitions, emission);
        return table;
    }

    private static void fill(final byte[] table, final List<Block> blocks, final BedrockCustomBlockDefinition[] definitions, final boolean emission) {
        for (int i = 0; i < blocks.size(); i++) {
            final BedrockCustomBlockDefinition definition = definitions[i];
            final int stateId = Block.getId(blocks.get(i).defaultBlockState());
            if (stateId < table.length) {
                table[stateId] = (byte) (definition == null ? 0 : emission ? definition.lightEmission() : definition.lightFilter());
            }
        }
    }

}
