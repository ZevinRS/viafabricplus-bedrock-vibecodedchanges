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

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.building.BedrockInputReplay;
import net.minecraft.client.Minecraft;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerAuthInputData;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.entity.MoverType;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    protected Vec3 stuckSpeedMultiplier;

    @Shadow
    public abstract boolean isSwimming();

    @Inject(method = "setSwimming", at = @At("HEAD"))
    private void trackSwimming(final boolean swimming, final CallbackInfo ci) {
        if (!ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return;
        }

        final UserConnection connection = ViaFabricPlus.api().userConnection();
        if (connection != null && swimming != this.isSwimming()) {
            connection.get(EntityTracker.class).getClientPlayer().addAuthInputData(swimming ? PlayerAuthInputData.StartSwimming : PlayerAuthInputData.StopSwimming);
        }
    }

    @Redirect(method = "makeStuckInBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;stuckSpeedMultiplier:Lnet/minecraft/world/phys/Vec3;", opcode = Opcodes.PUTFIELD))
    private void prioritySlowestMovementMultiplier(final Entity instance, final Vec3 value) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) && this.stuckSpeedMultiplier != Vec3.ZERO) {
            this.stuckSpeedMultiplier = new Vec3(Math.min(this.stuckSpeedMultiplier.x, value.x), Math.min(this.stuckSpeedMultiplier.y, value.y), Math.min(this.stuckSpeedMultiplier.z, value.z));
        } else {
            this.stuckSpeedMultiplier = value;
        }
    }

    // Bedrock ignores the box a vehicle would apply to its passengers
    @ModifyExpressionValue(method = "getFluidInteractionBox", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getVehicle()Lnet/minecraft/world/entity/Entity;"))
    private Entity skipPassengerChanges(final Entity vehicle) {
        return ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) ? null : vehicle;
    }

    // Bedrock inflates the box before deflating it, which is the same as inflating the deflated box
    @ModifyReturnValue(method = "getFluidInteractionBox", at = @At("RETURN"))
    private @Nullable AABB inflateFluidInteractionBox(final @Nullable AABB box) {
        if (box != null && ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return box.inflate(0, -0.4, 0);
        } else {
            return box;
        }
    }

    /**
     * The mouse doesn't turn the player while an input replay sets the rotation of every tick.
     */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void keepReplayRotation(final double yaw, final double pitch, final CallbackInfo ci) {
        if (BedrockInputReplay.isPlaying() && (Object) this == Minecraft.getInstance().player) {
            ci.cancel();
        }
    }

    @Unique
    private Vec3 viaFabricPlusBedrock$replayCollided;

    @Inject(method = "collide", at = @At("RETURN"))
    private void rememberReplayCollision(final Vec3 movement, final CallbackInfoReturnable<Vec3> cir) {
        if (BedrockInputReplay.isPlaying() && (Object) this == Minecraft.getInstance().player) {
            this.viaFabricPlusBedrock$replayCollided = cir.getReturnValue();
        }
    }

    /**
     * Logs every movement of the player during an input replay, for comparing with the Bedrock client.
     */
    @Inject(method = "move", at = @At("TAIL"))
    private void logReplayMovement(final MoverType type, final Vec3 movement, final CallbackInfo ci) {
        final Entity entity = (Entity) (Object) this;
        if (!BedrockInputReplay.isPlaying() || entity != Minecraft.getInstance().player || type != MoverType.SELF) {
            return;
        }
        final Vec3 collided = this.viaFabricPlusBedrock$replayCollided;
        final Vec3 velocity = entity.getDeltaMovement();
        ViaFabricPlusBedrock.impl().logger().info(String.format(java.util.Locale.ROOT,
            "[replay-move] frame=%d intended=(%.5f,%.5f,%.5f) collided=(%.5f,%.5f,%.5f) velocity=(%.5f,%.5f,%.5f) onGround=%s horizontal=%s minor=%s vertical=%s",
            BedrockInputReplay.frameIndex() - 1, movement.x, movement.y, movement.z,
            collided != null ? collided.x : Double.NaN, collided != null ? collided.y : Double.NaN, collided != null ? collided.z : Double.NaN,
            velocity.x, velocity.y, velocity.z, entity.onGround(), entity.horizontalCollision, entity.minorHorizontalCollision, entity.verticalCollision));
    }

}
