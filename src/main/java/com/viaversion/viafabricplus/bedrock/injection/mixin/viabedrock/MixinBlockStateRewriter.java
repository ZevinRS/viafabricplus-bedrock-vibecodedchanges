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

package com.viaversion.viafabricplus.bedrock.injection.mixin.viabedrock;

import com.google.common.collect.BiMap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.bedrock.block.BedrockCustomBlockMapping;
import com.viaversion.viafabricplus.bedrock.injection.access.IBlockStateRewriter;
import com.viaversion.viaversion.libs.fastutil.ints.Int2IntMap;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.raphimc.viabedrock.api.model.BedrockBlockState;
import net.raphimc.viabedrock.api.model.BlockState;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.BedrockMappingData;
import net.raphimc.viabedrock.protocol.model.BlockProperties;
import net.raphimc.viabedrock.protocol.rewriter.BlockStateRewriter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockStateRewriter.class, remap = false)
public abstract class MixinBlockStateRewriter implements IBlockStateRewriter {

    @Shadow
    @Final
    private Int2IntMap blockStateIdMappings;

    @Shadow
    @Final
    private BiMap<BlockState, Integer> blockStateMappings;

    // The blocks the server defines, in the order the Bedrock client numbers them
    @Unique
    private List<String> viaFabricPlusBedrock$customBlocks = List.of();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void rememberCustomBlocks(final BlockProperties[] blockProperties, final boolean hashedRuntimeBlockIds, final CallbackInfo ci) {
        this.viaFabricPlusBedrock$customBlocks = Arrays.stream(blockProperties).map(BlockProperties::name).toList();
        BedrockCustomBlockMapping.assign(blockProperties, this.blockStateMappings, this.blockStateIdMappings);
    }

    @Override
    public List<String> viaFabricPlusBedrock$getCustomBlocks() {
        return this.viaFabricPlusBedrock$customBlocks;
    }

    /**
     * Vanilla blocks the Bedrock client only has when the server defines them in start_game, like a custom block:
     * Dragonfly sends exactly these as block definitions. ViaBedrock counted them as built in, so on a server that
     * doesn't define them, like The Hive, every block state sorted after them got the wrong runtime ID.
     */
    @Unique
    private static final Pattern SERVER_DEFINED_VANILLA_BLOCKS = Pattern.compile(
        "minecraft:(?:[a-z_]+_(?:wool|concrete)_(?:stairs|slab|double_slab)|red_shrub|shelf_mushroom)");

    @WrapOperation(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/raphimc/viabedrock/protocol/data/BedrockMappingData;getBedrockBlockStates()Ljava/util/Set;", ordinal = 0))
    private Set<BedrockBlockState> withoutUndefinedServerBlocks(final BedrockMappingData mappings, final Operation<Set<BedrockBlockState>> original,
                                                                @Local(argsOnly = true) final BlockProperties[] blockProperties) {
        final Set<String> defined = Arrays.stream(blockProperties).map(properties -> properties.name().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        final Set<BedrockBlockState> states = new LinkedHashSet<>(original.call(mappings));
        states.removeIf(state -> SERVER_DEFINED_VANILLA_BLOCKS.matcher(state.namespacedIdentifier()).matches() && !defined.contains(state.namespacedIdentifier()));
        return states;
    }

    /**
     * The blocks for the blocks servers define aren't vanilla blocks with a waterlogged property, see
     * {@link BedrockCustomBlockMapping}.
     */
    @Inject(method = "waterlog", at = @At("HEAD"), cancellable = true)
    private void dontWaterlogCustomBlocks(final int javaBlockStateId, final CallbackInfoReturnable<Integer> cir) {
        if (!BedrockProtocol.MAPPINGS.getJavaBlockStates().containsValue(javaBlockStateId)) {
            cir.setReturnValue(javaBlockStateId);
        }
    }

}
