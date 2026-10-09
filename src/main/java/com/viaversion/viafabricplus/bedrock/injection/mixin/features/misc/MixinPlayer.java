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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.misc;

import com.viaversion.viafabricplus.bedrock.feature.Features;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayer {

    /**
     * Java slows the attacker and stops sprinting when a fully charged attack knocks back, which the Bedrock client
     * doesn't do (recorded on BDS and Dragonfly). Stopping to sprint for a tick would also add Java's sprint speed on top
     * of the server's, see {@link com.viaversion.viafabricplus.bedrock.injection.mixin.viabedrock.MixinLivingEntity}.
     */
    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private boolean skipJavaAttackSlowdown(final Player instance, final Vec3 deltaMovement) {
        return !Features.ATTACK_SLOWDOWN.isActive();
    }

    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
    private boolean skipJavaAttackSprintReset(final Player instance, final boolean sprinting) {
        return !Features.ATTACK_SLOWDOWN.isActive();
    }

    @Inject(method = "blockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void bedrockPickRange(final CallbackInfoReturnable<Double> cir) {
        final Player player = (Player) (Object) this;
        if (player instanceof LocalPlayer && Features.REACH.isActive()) {
            cir.setReturnValue(player.hasInfiniteMaterials() ? Features.CREATIVE_BLOCK_REACH.get() : Features.SURVIVAL_BLOCK_REACH.get());
        }
    }

    /**
     * Bedrock reaches entities 3 blocks away, 5 in creative. Java gets the creative range from the server, which
     * ViaBedrock doesn't send, so it stayed at 3 in creative.
     */
    @Inject(method = "entityInteractionRange", at = @At("HEAD"), cancellable = true)
    private void bedrockAttackRange(final CallbackInfoReturnable<Double> cir) {
        final Player player = (Player) (Object) this;
        if (player instanceof LocalPlayer && Features.REACH.isActive()) {
            cir.setReturnValue(player.hasInfiniteMaterials() ? Features.CREATIVE_ENTITY_REACH.get() : Features.SURVIVAL_ENTITY_REACH.get());
        }
    }

}
