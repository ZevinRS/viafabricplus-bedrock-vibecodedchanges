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
import com.viaversion.viafabricplus.bedrock.building.BedrockItemUse;
import java.util.Set;
import net.raphimc.viabedrock.api.util.MathUtil;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerAuthInputData;
import net.raphimc.viabedrock.protocol.model.Position2f;
import net.raphimc.viabedrock.protocol.packet.ClientPlayerPackets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

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

}
