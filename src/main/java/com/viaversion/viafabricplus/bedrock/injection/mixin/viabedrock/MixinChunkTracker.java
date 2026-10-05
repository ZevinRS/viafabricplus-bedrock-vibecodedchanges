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
import com.viaversion.viafabricplus.bedrock.building.BedrockPlacementState;
import com.viaversion.viaversion.api.minecraft.chunks.DataPalette;
import com.viaversion.viaversion.api.minecraft.chunks.PaletteType;
import java.util.List;
import net.raphimc.viabedrock.api.chunk.section.BedrockChunkSection;
import com.viaversion.viaversion.api.connection.StoredObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import net.raphimc.viabedrock.protocol.storage.ChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkTracker.class, remap = false)
public abstract class MixinChunkTracker extends StoredObject {

    private MixinChunkTracker(final UserConnection user) {
        super(user);
    }

    /**
     * A block the client placed is what the server says from its next update on.
     */
    @Inject(method = "handleBlockChange", at = @At("HEAD"))
    private void forgetPredictedBlock(final BlockPosition blockPosition, final int layer, final int blockState, final CallbackInfoReturnable<?> cir) {
        final BedrockPlacementState state = this.user().get(BedrockPlacementState.class);
        if (layer == 0 && state != null) {
            state.onServerBlockChange(blockPosition);
        }
    }

    /**
     * A sub chunk can arrive again after it was loaded, for example when the server sends it again for a request the
     * client repeated. ViaBedrock only expects the first one and disconnects. The Bedrock client uses the newest one.
     */
    @WrapOperation(method = "mergeSubChunk", at = @At(value = "INVOKE", target = "Lnet/raphimc/viabedrock/api/chunk/section/BedrockChunkSection;mergeWith(Lnet/raphimc/viabedrock/api/chunk/section/BedrockChunkSection;)V"))
    private void replaceLoadedSubChunk(final BedrockChunkSection section, final BedrockChunkSection other, final Operation<Void> original) {
        if (section.hasPendingBlockUpdates()) {
            original.call(section, other);
            return;
        }
        final List<DataPalette> blockPalettes = section.palettes(PaletteType.BLOCKS);
        blockPalettes.clear();
        blockPalettes.addAll(other.palettes(PaletteType.BLOCKS));
    }

}
