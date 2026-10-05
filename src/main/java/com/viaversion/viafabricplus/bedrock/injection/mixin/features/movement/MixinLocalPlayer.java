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

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.building.BedrockItemUse;
import net.minecraft.client.player.LocalPlayer;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Shadow
    protected abstract boolean isSprintingPossible(final boolean allowedInShallowWater);

    @Redirect(method = {"shouldStopRunSprinting", "canStartSprinting"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSprintingPossible(Z)Z"))
    private boolean allowNonSwimWaterSprinting(final LocalPlayer instance, final boolean allowedInShallowWater) {
        return this.isSprintingPossible(allowedInShallowWater || ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) && (instance.isSwimming() || instance.onGround()));
    }

    @Inject(method = "modifyInput", at = @At("HEAD"))
    private void rememberItemUseSlowdown(final Vec2 input, final CallbackInfoReturnable<Vec2> cir) {
        final LocalPlayer player = (LocalPlayer) (Object) this;
        BedrockItemUse.setSlowedByItemUse(player.isUsingItem() && !player.isPassenger());
    }

    /**
     * Bedrock slows the movement input to 0.1225 while using an item, Java to 0.2.
     */
    @Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void bedrockItemUseSpeed(final CallbackInfoReturnable<Float> cir) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            cir.setReturnValue(BedrockItemUse.ITEM_USE_SPEED_MULTIPLIER);
        }
    }

}
