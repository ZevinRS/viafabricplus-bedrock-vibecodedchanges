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

import com.viaversion.viafabricplus.bedrock.feature.Features;
import com.google.common.collect.BiMap;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.injection.access.IBlockStateRewriter;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockCustomBlockPack;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockPackImages;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockPackIndex;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.libs.fastutil.ints.Int2IntMap;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.raphimc.viabedrock.api.model.BedrockBlockState;
import net.raphimc.viabedrock.api.model.BlockState;
import net.raphimc.viabedrock.protocol.model.BlockProperties;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Gives every block state a server defines a block of the pool, see {@link BedrockCustomBlocks}. ViaBedrock drew them
 * all as one vanilla block, which also had the wrong collision for most of them.
 */
public final class BedrockCustomBlockMapping {

    private BedrockCustomBlockMapping() {
    }

    /**
     * Called once ViaBedrock built the block palette of a server: gives the blocks it defined blocks of the pool and
     * makes their models from the server's packs.
     */
    public static void onPaletteBuilt(final UserConnection user, final IBlockStateRewriter rewriter) {
        final ResourcePackStorage packs = user.get(ResourcePackStorage.class);
        if (!Features.CUSTOM_BLOCKS.isEnabled()) {
            BedrockCustomBlocks.setDefinitions(new BedrockCustomBlockDefinition[BedrockCustomBlocks.FULL_BLOCKS], new BedrockCustomBlockDefinition[BedrockCustomBlocks.SHAPED_BLOCKS]);
            return;
        }
        assign(rewriter.viaFabricPlusBedrock$getBlockProperties(), rewriter.viaFabricPlusBedrock$getBedrockStates(), rewriter.viaFabricPlusBedrock$getJavaStates(), packs);
        if (packs != null && BedrockCustomBlocks.hasDefinitions()) {
            BedrockCustomBlockPack.build(packs);
        }
    }

    /**
     * Called when ViaBedrock built the block palette of a server.
     *
     * @param blockProperties     the blocks the server defined
     * @param bedrockStates       ViaBedrock's Bedrock block states and their runtime ids
     * @param javaStatesByBedrock ViaBedrock's Java block state of every Bedrock runtime id, which is changed here
     * @param packs               the server's packs, which tell which full blocks are see-through
     */
    private static void assign(final BlockProperties[] blockProperties, final BiMap<BlockState, Integer> bedrockStates, final Int2IntMap javaStatesByBedrock,
                               final @Nullable ResourcePackStorage packs) {
        final Map<String, CompoundTag> customBlocks = new HashMap<>();
        for (final BlockProperties properties : blockProperties) {
            // Vanilla blocks some servers define, like Dragonfly's wool stairs, are left to ViaBedrock
            if (properties.properties().get("vanilla_block_data") instanceof CompoundTag && !properties.name().toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
                customBlocks.putIfAbsent(properties.name().toLowerCase(Locale.ROOT), properties.properties());
            }
        }
        final BedrockCustomBlockDefinition[] full = new BedrockCustomBlockDefinition[BedrockCustomBlocks.FULL_BLOCKS];
        final BedrockCustomBlockDefinition[] shaped = new BedrockCustomBlockDefinition[BedrockCustomBlocks.SHAPED_BLOCKS];
        if (customBlocks.isEmpty()) {
            BedrockCustomBlocks.setDefinitions(full, shaped);
            return;
        }

        final List<Map.Entry<BlockState, Integer>> states = new ArrayList<>(bedrockStates.entrySet());
        states.sort(Comparator.comparingInt(Map.Entry::getValue));
        int fullCount = 0;
        int shapedCount = 0;
        int unassigned = 0;
        for (final Map.Entry<BlockState, Integer> entry : states) {
            if (!(entry.getKey() instanceof final BedrockBlockState state) || !customBlocks.containsKey(state.namespacedIdentifier())) {
                continue;
            }
            final String name = state.namespacedIdentifier();
            final CompoundTag stateProperties = state.blockStateTag().get("states") instanceof final CompoundTag tag ? tag : new CompoundTag();
            final BedrockCustomBlockDefinition definition = BedrockCustomBlockDefinitions.create(name, customBlocks.get(name), stateProperties);
            final Block block;
            if (BedrockCustomBlockDefinitions.isFullBlock(definition) && isOpaque(definition, packs) && fullCount < full.length) {
                full[fullCount] = definition;
                block = BedrockCustomBlocks.fullBlock(fullCount++);
            } else if (shapedCount < shaped.length) {
                shaped[shapedCount] = definition;
                block = BedrockCustomBlocks.shapedBlock(shapedCount++);
            } else {
                unassigned++;
                continue;
            }
            javaStatesByBedrock.put(entry.getValue().intValue(), Block.getId(block.defaultBlockState()));
        }
        BedrockCustomBlocks.setDefinitions(full, shaped);
        ViaFabricPlusBedrock.impl().logger().info("Mapped the server's block states to {} full and {} shaped blocks{}", fullCount, shapedCount,
            unassigned > 0 ? ", " + unassigned + " didn't fit" : "");
    }

    /**
     * Full blocks hide the faces of the blocks next to them, so see-through ones like leaves are given shaped blocks,
     * which don't.
     */
    private static boolean isOpaque(final BedrockCustomBlockDefinition definition, final @Nullable ResourcePackStorage packs) {
        if (definition.renderMethod() != null && !definition.renderMethod().equals("opaque")) {
            return false;
        }
        if (packs == null) {
            return true;
        }
        final BedrockPackIndex index = BedrockPackIndex.of(packs);
        final Map<String, String> textures = definition.textures().isEmpty() ? index.blockTextures(definition.name()) : definition.textures();
        for (final String shortName : textures.values()) {
            final String path = index.terrainTexture(shortName);
            final BufferedImage image = path != null ? BedrockPackImages.get(packs, path) : null;
            if (image != null && hasTransparency(image)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasTransparency(final BufferedImage image) {
        if (!image.getColorModel().hasAlpha()) {
            return false;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) >>> 24 < 255) {
                    return true;
                }
            }
        }
        return false;
    }

}
