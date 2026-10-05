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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.ViaFabricPlus;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_VelocityCutoff {

    private static final double BEDROCK_VELOCITY_CUTOFF = 1.0E-7;

    /**
     * Java zeroes velocity components below 0.003 every tick, Bedrock only lets them decay to about 1e-7, as recorded from
     * the Bedrock client. For example, a small sideways speed while running against a wall carries into a jump on Bedrock.
     * The velocity is set again from before Java's cutoff, since ViaFabricPlus already changes the cutoff constant.
     */
    @WrapOperation(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"))
    private void bedrockVelocityCutoff(final LivingEntity instance, final double x, final double y, final double z, final Operation<Void> original) {
        if (!ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            original.call(instance, x, y, z);
            return;
        }
        final Vec3 velocity = instance.getDeltaMovement();
        original.call(instance, cutoff(velocity.x), cutoff(velocity.y), cutoff(velocity.z));
    }

    private static double cutoff(final double value) {
        return Math.abs(value) < BEDROCK_VELOCITY_CUTOFF ? 0.0 : value;
    }

}
