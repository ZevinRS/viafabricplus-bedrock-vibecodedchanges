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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.block;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.building.BedrockBuilding;
import com.viaversion.viafabricplus.bedrock.building.BedrockItemUse;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.phys.BlockHitResult;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {

    /**
     * ViaBedrock doesn't handle CHANGE_DESTROY_DIRECTION (sent when the targeted face changes while mining) and
     * disconnects. Bedrock servers don't need it, the face the block is broken from is sent when it breaks.
     */
    @WrapWithCondition(method = "continueDestroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private boolean skipChangeDestroyDirection(final ClientPacketListener connection, final Packet<?> packet) {
        return !(packet instanceof final ServerboundPlayerActionPacket actionPacket
            && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.CHANGE_DESTROY_DIRECTION
            && BedrockProtocolVersion.BEDROCK_LATEST.equals(ViaFabricPlus.api().targetVersion()));
    }

    @Unique
    private boolean viaFabricPlusBedrock$placingBlock;

    @Unique
    private boolean viaFabricPlusBedrock$usingBucket;

    @Inject(method = "performUseItemOn", at = @At("HEAD"))
    private void checkPlacingBlock(final LocalPlayer player, final InteractionHand hand, final BlockHitResult hit, final CallbackInfoReturnable<InteractionResult> cir) {
        this.viaFabricPlusBedrock$placingBlock = hand == InteractionHand.MAIN_HAND && player.getMainHandItem().getItem() instanceof BlockItem && BedrockBuilding.isActive();
        this.viaFabricPlusBedrock$usingBucket = hand == InteractionHand.MAIN_HAND && player.getMainHandItem().getItem() instanceof BucketItem && BedrockBuilding.isActive();
    }

    /**
     * The Bedrock client sends placements depending on whether they succeeded, which the packet doesn't tell.
     */
    @Inject(method = "performUseItemOn", at = @At("RETURN"))
    private void recordPlacement(final LocalPlayer player, final InteractionHand hand, final BlockHitResult hit, final CallbackInfoReturnable<InteractionResult> cir) {
        if (this.viaFabricPlusBedrock$placingBlock) {
            this.viaFabricPlusBedrock$placingBlock = false;
            BedrockBuilding.recordPlacement(hit, cir.getReturnValue());
        }
        if (this.viaFabricPlusBedrock$usingBucket) {
            this.viaFabricPlusBedrock$usingBucket = false;
            BedrockItemUse.recordBucketUse(player, true, cir.getReturnValue().consumesAction());
        }
    }

    /**
     * Bedrock uses buckets on what they look at, which is found before the packet is sent.
     */
    @Inject(method = "useItem", at = @At("HEAD"))
    private void recordBucketUse(final Player player, final InteractionHand hand, final CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND && player instanceof final LocalPlayer localPlayer && !player.isSpectator()
            && player.getMainHandItem().getItem() instanceof BucketItem && BedrockBuilding.isActive()) {
            BedrockItemUse.recordBucketUse(localPlayer, false, false);
        }
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void startUsingItem(final Player player, final InteractionHand hand, final CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND && player instanceof final LocalPlayer localPlayer) {
            BedrockItemUse.onItemUsed(localPlayer);
        }
    }

}
