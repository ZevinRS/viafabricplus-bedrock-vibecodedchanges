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

import com.viaversion.viafabricplus.bedrock.feature.Features;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.bedrock.building.BedrockBlockBreak;
import com.viaversion.viafabricplus.bedrock.building.BedrockPlacementState;
import com.viaversion.viafabricplus.bedrock.building.BedrockPlacementTranslator;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerActionType;
import net.raphimc.viabedrock.protocol.packet.ClientPlayerPackets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The block actions of breaking blocks with client authoritative block breaking, like The Hive uses, as the Bedrock
 * client sends them there. Once a block broke it sends stop_break at 0 0 0 with face 0 and a last crack_break, but no
 * abort_break; ViaBedrock also aborted the broken block right away, which made the server cancel the break. The
 * broken block is only aborted once the next one starts breaking, or the attack ends, see {@link BedrockPlacementTranslator}.
 * The break itself is reported with the next input, see {@link BedrockBlockBreak}.
 */
@Mixin(value = ClientPlayerPackets.class, remap = false)
public abstract class MixinBlockBreakActions {

    private static final String ADD_BLOCK_ACTION = "Lnet/raphimc/viabedrock/api/model/entity/ClientPlayerEntity;addAuthInputBlockAction(Lnet/raphimc/viabedrock/api/model/entity/ClientPlayerEntity$AuthInputBlockAction;)V";

    @WrapOperation(method = "lambda$register$9", at = @At(value = "INVOKE", target = ADD_BLOCK_ACTION, ordinal = 0))
    private static void abortBrokenBlockBeforeStart(final ClientPlayerEntity player, final ClientPlayerEntity.AuthInputBlockAction action, final Operation<Void> original,
                                                    @Local(argsOnly = true) final PacketWrapper wrapper) {
        final BedrockPlacementState state = BedrockPlacementTranslator.state(wrapper.user());
        if (state.brokenPosition() != null && Features.BLOCK_BREAKING.isEnabled()) {
            original.call(player, new ClientPlayerEntity.AuthInputBlockAction(PlayerActionType.AbortDestroyBlock, state.brokenPosition(), 0));
            state.setBrokenPosition(null);
        }
        original.call(player, action);
    }

    @WrapOperation(method = "lambda$register$9", at = @At(value = "INVOKE", target = ADD_BLOCK_ACTION, ordinal = 2))
    private static void stopAtOrigin(final ClientPlayerEntity player, final ClientPlayerEntity.AuthInputBlockAction action, final Operation<Void> original) {
        if (!Features.BLOCK_BREAKING.isEnabled()) {
            original.call(player, action);
            return;
        }
        original.call(player, new ClientPlayerEntity.AuthInputBlockAction(PlayerActionType.StopDestroyBlock, new BlockPosition(0, 0, 0), 0));
    }

    @WrapOperation(method = "lambda$register$9", at = @At(value = "INVOKE", target = ADD_BLOCK_ACTION, ordinal = 3))
    private static void reportBrokenBlock(final ClientPlayerEntity player, final ClientPlayerEntity.AuthInputBlockAction action, final Operation<Void> original,
                                          @Local(argsOnly = true) final PacketWrapper wrapper) {
        original.call(player, action);
        if (Features.BLOCK_BREAKING.isEnabled()) {
            BedrockBlockBreak.onBlockBroken(wrapper.user(), action.position(), action.direction());
        }
    }

    @WrapOperation(method = "lambda$register$9", at = @At(value = "INVOKE", target = ADD_BLOCK_ACTION, ordinal = 4))
    private static void keepBrokenBlock(final ClientPlayerEntity player, final ClientPlayerEntity.AuthInputBlockAction action, final Operation<Void> original,
                                        @Local(argsOnly = true) final PacketWrapper wrapper) {
        if (!Features.BLOCK_BREAKING.isEnabled()) {
            original.call(player, action);
            return;
        }
        final BedrockPlacementState state = BedrockPlacementTranslator.state(wrapper.user());
        state.setBrokenPosition(action.position());
        // The last crack_break of the broken block counts for this tick
        state.setCrackedThisTick();
    }

}
