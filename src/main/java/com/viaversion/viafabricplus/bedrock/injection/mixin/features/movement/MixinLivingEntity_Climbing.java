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
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Climbing as recorded from the Bedrock client on ladders and vines: there is no horizontal speed limit, holding jump
 * moves up at 0.2 right away, and a tick that climbs because of a horizontal collision keeps its 0.2 speed without
 * gravity (MobMovementClimb::applyAutoClimbing adds the AutoClimbTravelFlag, which the gravity systems exclude).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_Climbing {

    @Shadow
    protected boolean jumping;

    @Shadow
    public abstract boolean onClimbable();

    @Unique
    private boolean viaFabricPlusBedrock$climbedWithoutGravity;

    @Unique
    private boolean viaFabricPlusBedrock$isBedrockPlayer() {
        return (Object) this == Minecraft.getInstance().player && ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST);
    }

    @WrapOperation(method = "handleRelativeFrictionAndCalculateMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;handleOnClimbable(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 bedrockClimbing(final LivingEntity instance, final Vec3 delta, final Operation<Vec3> original) {
        final Vec3 climbing = original.call(instance, delta);
        if (!this.viaFabricPlusBedrock$isBedrockPlayer() || !this.onClimbable()) {
            return climbing;
        }
        return new Vec3(delta.x, this.jumping ? 0.2 : climbing.y, delta.z);
    }

    @WrapOperation(method = "travelInAir", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;handleRelativeFrictionAndCalculateMovement(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 rememberAutoClimb(final LivingEntity instance, final Vec3 input, final float friction, final Operation<Vec3> original) {
        final Vec3 movement = original.call(instance, input, friction);
        this.viaFabricPlusBedrock$climbedWithoutGravity = instance.horizontalCollision && this.onClimbable() && this.viaFabricPlusBedrock$isBedrockPlayer();
        return movement;
    }

    @WrapOperation(method = "travelInAir", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V"))
    private void noGravityWhileAutoClimbing(final LivingEntity instance, final double x, final double y, final double z, final Operation<Void> original) {
        if (this.viaFabricPlusBedrock$climbedWithoutGravity) {
            this.viaFabricPlusBedrock$climbedWithoutGravity = false;
            original.call(instance, x, 0.2, z);
        } else {
            original.call(instance, x, y, z);
        }
    }

}
