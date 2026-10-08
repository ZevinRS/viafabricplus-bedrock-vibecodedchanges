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
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.building.BedrockDimensionChange;
import com.viaversion.viafabricplus.bedrock.building.BedrockSprint;
import com.viaversion.viafabricplus.bedrock.building.BedrockAuthInput;
import com.viaversion.viafabricplus.bedrock.building.BedrockItemUse;
import net.minecraft.client.player.LocalPlayer;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerAuthInputData;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import com.viaversion.viaversion.api.connection.UserConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer {

    @Shadow
    protected abstract boolean isSprintingPossible(final boolean allowedInShallowWater);

    /**
     * Bedrock only keeps the player from sprinting in water while it jumps there without swimming or standing on the
     * ground, as recorded: sprinting into the water kept the sprint, pressing sprint while jumping in the water started
     * and stopped it every tick.
     */
    @Redirect(method = {"shouldStopRunSprinting", "canStartSprinting"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSprintingPossible(Z)Z"))
    private boolean bedrockWaterSprinting(final LocalPlayer instance, final boolean allowedInShallowWater) {
        if (!ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return this.isSprintingPossible(allowedInShallowWater);
        }
        // Bedrock still counts the player in the water in the tick it got out, as recorded when climbing out of water
        final boolean inWater = instance.isInWater() || BedrockSprint.wasInWaterLastTick();
        final boolean jumpingInWater = inWater && !instance.isSwimming() && !instance.onGround() && instance.input.keyPresses.jump();
        return this.isSprintingPossible(true) && !jumpingInWater;
    }

    /**
     * Bedrock keeps sprinting through the loading screen of a dimension change and only stops once it acknowledged
     * the change, even though it ignores the keys in between.
     */
    @Inject(method = {"shouldStopRunSprinting", "shouldStopSwimSprinting"}, at = @At("HEAD"), cancellable = true)
    private void keepSprintingWhileLoading(final CallbackInfoReturnable<Boolean> cir) {
        if (BedrockDimensionChange.isLoading() && ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void rememberSprintingAtTickStart(final CallbackInfo ci) {
        BedrockSprint.onTickStart(((LocalPlayer) (Object) this).isInWater());
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void prepareAuthInput(final CallbackInfo ci) {
        BedrockAuthInput.setVelocity(((LocalPlayer) (Object) this).getDeltaMovement());
        final UserConnection connection = ViaFabricPlus.api().userConnection();
        if (connection == null || !ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return;
        }
        final ClientPlayerEntity player = connection.get(EntityTracker.class).getClientPlayer();
        if (BedrockSprint.startedThisTick()) {
            player.addAuthInputData(PlayerAuthInputData.StartSprinting);
        }
        if (BedrockSprint.stoppedThisTick()) {
            player.addAuthInputData(PlayerAuthInputData.StopSprinting);
        }
    }

    // Pressing back doesn't cancel a double tap of forward to sprint on Bedrock, as recorded with forward and back held for a tick
    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Input;backward()Z"))
    private boolean keepSprintDoubleTap(final boolean backward) {
        return !ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) && backward;
    }

    /**
     * The pose follows the swimming a tick later on Bedrock (see MixinPlayer), so the tick after the swimming stopped out
     * of the water would count as crawling on Java and slow the input. Bedrock doesn't slow it, as recorded when
     * climbing out of water.
     */
    @WrapOperation(method = "isMovingSlowly", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isVisuallyCrawling()Z"))
    private boolean crawlOnlyWhileSwimming(final LocalPlayer instance, final Operation<Boolean> original) {
        return original.call(instance) && (!ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) || instance.isSwimming());
    }

    /**
     * Java only starts swimming at the start of the tick, so sprinting off in water swims from the next tick on. Bedrock
     * swims in the tick the sprint started, as recorded when sprinting off underwater.
     */
    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;aiStep()V"))
    private void swimAfterSprintStart(final CallbackInfo ci) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            ((LocalPlayer) (Object) this).updateSwimming();
        }
    }

    /**
     * Java stops sprinting when the player collides horizontally, unless the collision was minor. Bedrock only does when
     * the move was blocked along the axis the player mostly tried to move along, so sliding along a wall keeps
     * sprinting (SprintTriggerSystem::doIntentTick).
     */
    @ModifyExpressionValue(method = "shouldStopRunSprinting", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;horizontalCollision:Z"))
    private boolean bedrockSprintCollision(final boolean horizontalCollision) {
        return ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) ? BedrockSprint.isMainAxisBlocked() : horizontalCollision;
    }

    @ModifyExpressionValue(method = "shouldStopRunSprinting", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/LocalPlayer;minorHorizontalCollision:Z"))
    private boolean noMinorCollisionOnBedrock(final boolean minorHorizontalCollision) {
        return !ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) && minorHorizontalCollision;
    }

    /**
     * Java scales diagonal input up to the unit square, so moving diagonally is as fast as straight. Bedrock keeps the
     * normalized input (recorded: diagonal air acceleration 0.026 * 0.98 on Bedrock, 0.026 on Java).
     */
    @WrapOperation(method = "modifyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;modifyInputSpeedForSquareMovement(Lnet/minecraft/world/phys/Vec2;)Lnet/minecraft/world/phys/Vec2;"))
    private Vec2 keepBedrockDiagonalInput(final Vec2 input, final Operation<Vec2> original) {
        return ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) ? input : original.call(input);
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
