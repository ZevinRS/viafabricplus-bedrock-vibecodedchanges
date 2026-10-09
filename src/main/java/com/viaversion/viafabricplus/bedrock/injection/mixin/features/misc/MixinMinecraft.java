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
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.bedrock.building.BedrockBuilding;
import com.viaversion.viafabricplus.bedrock.building.BedrockDimensionChange;
import com.viaversion.viafabricplus.bedrock.building.BedrockInputReplay;
import com.viaversion.viafabricplus.bedrock.building.BedrockItemUse;
import com.viaversion.viafabricplus.bedrock.building.BedrockPacketDelay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Shadow
    public abstract @Nullable Entity getCameraEntity();

    @Shadow
    public @Nullable LocalPlayer player;

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void startBedrockBuild(final CallbackInfo ci) {
        if (Features.BUILDING.isActive()) {
            BedrockBuilding.instance().startBuild();
        }
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult trackBedrockBuild(final MultiPlayerGameMode gameMode, final LocalPlayer player, final InteractionHand hand, final BlockHitResult hit, final Operation<InteractionResult> original) {
        if (!BedrockBuilding.isActive() || hand != InteractionHand.MAIN_HAND) {
            return original.call(gameMode, player, hand, hit);
        }
        final BlockPos placePos = BedrockBuilding.placementPosition(player, hand, hit);
        final InteractionResult result = original.call(gameMode, player, hand, hit);
        if (placePos != null && result instanceof InteractionResult.Success) {
            BedrockBuilding.instance().onBlockPlaced(placePos);
        }
        return result;
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;startUseItem()V", ordinal = 1))
    private void replaceUseRepeatWithBedrockBuilding(final Minecraft instance, final Operation<Void> original) {
        if (!BedrockBuilding.isActive() || this.player == null) {
            original.call(instance);
            return;
        }
        if (!Features.BUILDING.isEnabled()) {
            BedrockItemUse.setRepeating(true);
            try {
                original.call(instance);
            } finally {
                BedrockItemUse.setRepeating(false);
            }
            return;
        }
        // Holding use with a block places blocks through Bedrock's building instead of repeating the click every 4 ticks
        if (BedrockBuilding.isHoldingBlock(this.player)) {
            return;
        }
        BedrockItemUse.setRepeating(true);
        try {
            original.call(instance);
        } finally {
            BedrockItemUse.setRepeating(false);
        }
    }

    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void continueBedrockBuild(final float partialTicks, final CallbackInfo ci) {
        if (Features.BUILDING.isActive()) {
            BedrockBuilding.instance().frame((Minecraft) (Object) this, partialTicks);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void tickBedrockItemUse(final CallbackInfo ci) {
        BedrockItemUse.tick((Minecraft) (Object) this);
        BedrockInputReplay.tick((Minecraft) (Object) this);
        BedrockPacketDelay.tick((Minecraft) (Object) this);
        BedrockDimensionChange.tick((Minecraft) (Object) this);
    }

    @ModifyExpressionValue(method = "pick(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;"))
    private HitResult bedrockReachAroundRaycast(final HitResult hitResult) {
        if (Features.REACH_AROUND.isActive()) {
            final Entity entity = this.getCameraEntity();
            if (hitResult.getType() != HitResult.Type.MISS) return hitResult;
            if (!this.viaFabricPlusBedrock$canReachAround(entity)) return hitResult;

            final int x = Mth.floor(entity.getX());
            final int y = Mth.floor(entity.getY() - 0.2F);
            final int z = Mth.floor(entity.getZ());
            final BlockPos floorPos = new BlockPos(x, y, z);

            return new BlockHitResult(Vec3.atCenterOf(floorPos), entity.getDirection(), floorPos, false);
        }

        return hitResult;
    }

    @Unique
    private boolean viaFabricPlusBedrock$canReachAround(final Entity entity) {
        return entity.onGround() && entity.getVehicle() == null && entity.getXRot() >= Features.MIN_PITCH.get();
    }

}
