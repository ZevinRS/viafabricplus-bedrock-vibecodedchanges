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

import com.viaversion.viafabricplus.bedrock.block.BedrockCustomBlocks;
import net.raphimc.viabedrock.protocol.data.BedrockMappingData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ViaBedrock lights chunks with tables of the vanilla Java block states, which the blocks for the blocks servers define
 * come after, see {@link BedrockCustomBlocks}.
 */
@Mixin(value = BedrockMappingData.class, remap = false)
public abstract class MixinBedrockMappingData {

    @Inject(method = "getJavaBlockLightEmission", at = @At("RETURN"), cancellable = true)
    private void addCustomBlockLightEmission(final CallbackInfoReturnable<byte[]> cir) {
        cir.setReturnValue(BedrockCustomBlocks.lightEmissions(cir.getReturnValue()));
    }

    @Inject(method = "getJavaBlockOpacity", at = @At("RETURN"), cancellable = true)
    private void addCustomBlockOpacity(final CallbackInfoReturnable<byte[]> cir) {
        cir.setReturnValue(BedrockCustomBlocks.lightFilters(cir.getReturnValue()));
    }

}
