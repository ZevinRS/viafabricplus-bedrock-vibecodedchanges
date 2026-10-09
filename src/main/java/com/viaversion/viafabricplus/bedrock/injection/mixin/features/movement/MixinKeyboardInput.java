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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.movement;

import com.viaversion.viafabricplus.bedrock.feature.Features;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.building.BedrockDimensionChange;
import com.viaversion.viafabricplus.bedrock.building.BedrockInputReplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void playBedrockInputReplay(final CallbackInfo ci) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.input != (Object) this) {
            return;
        }
        final Vec2 moveVector = BedrockInputReplay.applyFrame(minecraft.player, this);
        if (moveVector != null) {
            this.moveVector = moveVector;
        }
        if (Features.DIMENSION_CHANGE.isActive()) {
            final Vec2 filtered = BedrockDimensionChange.filterInput(this);
            if (filtered != null) {
                this.moveVector = filtered;
            }
        }
    }

}
