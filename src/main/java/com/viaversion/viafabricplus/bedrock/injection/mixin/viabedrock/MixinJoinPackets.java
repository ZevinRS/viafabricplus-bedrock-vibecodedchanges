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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.bedrock.block.BedrockCustomBlockMapping;
import com.viaversion.viafabricplus.bedrock.injection.access.IBlockStateRewriter;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockNameTags;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.libs.fastutil.ints.IntIntImmutablePair;
import com.viaversion.viaversion.libs.fastutil.ints.IntIntPair;
import net.raphimc.viabedrock.protocol.model.BlockProperties;
import net.raphimc.viabedrock.protocol.packet.JoinPackets;
import net.raphimc.viabedrock.protocol.rewriter.BlockStateRewriter;
import net.raphimc.viabedrock.protocol.storage.GameSessionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JoinPackets.class, remap = false)
public abstract class MixinJoinPackets {

    /**
     * Forgets the name tags of the last server before its entities are translated, on the same thread. Clearing them
     * when Java handled the login lost the name tags of the entities the server spawned while the join was loading.
     */
    @Inject(method = "lambda$register$7", at = @At("HEAD"))
    private static void forgetNameTags(final PacketWrapper wrapper, final CallbackInfo ci) {
        BedrockNameTags.clear();
    }

    /**
     * A dimension definition holds its lowest Y and then its height, not its highest and lowest Y as ViaBedrock read
     * them. The Hive defines the overworld from 0 with a height of 256, which became a negative height and failed the
     * join. ViaBedrock passes the pair as (second value, first value), so it's (height, lowest Y) here.
     */
    @WrapOperation(method = "lambda$register$8", at = @At(value = "INVOKE", target = "Lnet/raphimc/viabedrock/protocol/storage/GameSessionStorage;putBedrockDimensionDefinition(Ljava/lang/String;Lcom/viaversion/viaversion/libs/fastutil/ints/IntIntPair;)V"))
    private static void readMinimumAndHeight(final GameSessionStorage gameSession, final String dimension, final IntIntPair heightAndMinimum, final Operation<Void> original) {
        final int minimumY = heightAndMinimum.rightInt();
        final int height = heightAndMinimum.leftInt();
        original.call(gameSession, dimension, new IntIntImmutablePair(minimumY, minimumY + height));
    }

    /**
     * Gives the blocks the server defined blocks of their own and makes their models, once ViaBedrock built the block
     * palette from them, see {@link BedrockCustomBlockMapping}.
     */
    @WrapOperation(method = "lambda$register$7", at = @At(value = "NEW", target = "net/raphimc/viabedrock/protocol/rewriter/BlockStateRewriter"))
    private static BlockStateRewriter makeCustomBlockModels(final BlockProperties[] blockProperties, final boolean hashedRuntimeBlockIds, final Operation<BlockStateRewriter> original,
                                                            @Local(argsOnly = true) final PacketWrapper wrapper) {
        final BlockStateRewriter blockStateRewriter = original.call(blockProperties, hashedRuntimeBlockIds);
        BedrockCustomBlockMapping.onPaletteBuilt(wrapper.user(), (IBlockStateRewriter) blockStateRewriter);
        return blockStateRewriter;
    }

}
