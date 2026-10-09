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

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.viaversion.viafabricplus.bedrock.building.BedrockAuthInput;
import com.viaversion.viafabricplus.bedrock.building.BedrockBlockBreak;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import net.raphimc.viabedrock.protocol.model.inventory.BedrockInventoryTransaction;
import org.spongepowered.asm.mixin.Unique;
import com.viaversion.viafabricplus.bedrock.building.BedrockItemUse;
import java.util.Set;
import net.raphimc.viabedrock.api.util.MathUtil;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerAuthInputData;
import net.raphimc.viabedrock.protocol.model.Position2f;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.packet.ClientPlayerPackets;
import com.viaversion.viafabricplus.bedrock.building.BedrockDimensionChange;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.viaversion.viafabricplus.bedrock.building.BedrockPlacementState;
import com.viaversion.viafabricplus.bedrock.building.BedrockPlacementTranslator;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = ClientPlayerPackets.class, remap = false)
public abstract class MixinClientPlayerPackets {

    /**
     * The Bedrock client sends its movement input slowed while using an item, but the raw input unchanged. The
     * selector matches all methods since the input is written in a lambda; the slowed input is the first call.
     */
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/raphimc/viabedrock/api/util/MathUtil;calculateMovementDirections(Ljava/util/Set;Z)Lnet/raphimc/viabedrock/protocol/model/Position2f;", ordinal = 0))
    private static Position2f slowMoveVectorWhileUsingItem(final Set<PlayerAuthInputData> authInputData, final boolean sneaking, final Operation<Position2f> original) {
        final Position2f moveVector = original.call(authInputData, sneaking);
        if (!BedrockItemUse.isSlowedByItemUse()) {
            return moveVector;
        }
        return new Position2f(moveVector.x() * BedrockItemUse.ITEM_USE_SPEED_MULTIPLIER, moveVector.y() * BedrockItemUse.ITEM_USE_SPEED_MULTIPLIER);
    }

    /**
     * Sends the player's real velocity as delta instead of ViaBedrock's estimate, which got water, lava, climbing and
     * other blocks wrong and was 2% off on ground and in air, as compared with the Bedrock client. The delta is the only
     * load of the sixth local in the player_auth_input handler.
     */
    @ModifyVariable(method = "lambda$register$18", at = @At(value = "LOAD", ordinal = 0), index = 6)
    private static Position3f sendRealVelocity(final Position3f estimated) {
        final Position3f velocity = BedrockAuthInput.velocity();
        return velocity != null ? velocity : estimated;
    }

    /**
     * Bedrock sends the yaw of its interact rotation from -270 to 90 degrees instead of -180 to 180, as recorded from the
     * Bedrock client. It's the third yaw read in the player_auth_input handler.
     */
    @WrapOperation(method = "lambda$register$18", at = @At(value = "INVOKE", target = "Lnet/raphimc/viabedrock/protocol/model/Position3f;y()F", ordinal = 2))
    private static float bedrockInteractYaw(final Position3f rotation, final Operation<Float> original) {
        final float yaw = original.call(rotation);
        return ((yaw + 270F) % 360F + 360F) % 360F - 270F;
    }

    /**
     * The Bedrock client keeps saying it sprints in the loading screen of a dimension change, where it ignores the
     * sprint key, see {@link BedrockDimensionChange}.
     */
    @Inject(method = "lambda$register$18", at = @At("HEAD"))
    private static void keepSprintingFlagWhileLoading(final PacketWrapper wrapper, final CallbackInfo ci) {
        if (BedrockDimensionChange.sendsSprinting()) {
            wrapper.user().get(EntityTracker.class).getClientPlayer().addAuthInputData(PlayerAuthInputData.Sprinting);
        }
    }

    /**
     * The Bedrock client aborts the block it broke last once it stops attacking, see {@link MixinBlockBreakActions}:
     * then no block was cracked in the tick.
     */
    @Inject(method = "lambda$register$18", at = @At("HEAD"))
    private static void abortBrokenBlockAfterAttack(final PacketWrapper wrapper, final CallbackInfo ci) {
        final BedrockPlacementState state = BedrockPlacementTranslator.state(wrapper.user());
        final boolean cracked = state.consumeCrackedThisTick();
        if (state.brokenPosition() != null && !cracked) {
            wrapper.user().get(EntityTracker.class).getClientPlayer().addAuthInputBlockAction(
                new ClientPlayerEntity.AuthInputBlockAction(PlayerActionType.AbortDestroyBlock, state.brokenPosition(), 0));
            state.setBrokenPosition(null);
        }
    }


    @Unique
    private static BedrockInventoryTransaction viaFabricPlusBedrock$breakTransaction;

    /**
     * A block broken in the tick is reported with the input, see {@link BedrockBlockBreak}.
     */
    @Inject(method = "lambda$register$18", at = @At("HEAD"))
    private static void prepareBlockBreak(final PacketWrapper wrapper, final CallbackInfo ci) {
        viaFabricPlusBedrock$breakTransaction = BedrockBlockBreak.prepareInput(wrapper.user());
    }

    /**
     * The first boolean of the input is the presence of the item use, which ViaBedrock always leaves out.
     */
    @WrapOperation(method = "lambda$register$18", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;write(Lcom/viaversion/viaversion/api/type/Type;Ljava/lang/Object;)V"))
    private static void writeBlockBreak(final PacketWrapper wrapper, final Type<?> type, final Object value, final Operation<Void> original) {
        final BedrockInventoryTransaction transaction = viaFabricPlusBedrock$breakTransaction;
        if (transaction != null && (Object) type == Types.BOOLEAN) {
            viaFabricPlusBedrock$breakTransaction = null;
            BedrockBlockBreak.writeInput(wrapper, transaction);
        } else {
            original.call(wrapper, type, value);
        }
    }

}
