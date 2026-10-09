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
import com.viaversion.viafabricplus.bedrock.building.BedrockDimensionChange;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.raphimc.viabedrock.api.util.PacketFactory;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.ServerboundLoadingScreenPacketType;
import net.raphimc.viabedrock.protocol.packet.WorldPackets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Leaves starting the loading screen of a dimension change to {@link BedrockDimensionChange}, which does it once the
 * client handled the change instead of when the packet arrived.
 */
@Mixin(value = WorldPackets.class, remap = false)
public abstract class MixinDimensionChangePackets {

    @WrapOperation(method = "lambda$register$2", at = @At(value = "INVOKE", target = "Lnet/raphimc/viabedrock/api/util/PacketFactory;sendBedrockLoadingScreen(Lcom/viaversion/viaversion/api/connection/UserConnection;Lnet/raphimc/viabedrock/protocol/data/enums/bedrock/generated/ServerboundLoadingScreenPacketType;Ljava/lang/Long;)V"))
    private static void startLoadingScreenLater(final UserConnection user, final ServerboundLoadingScreenPacketType type, final Long loadingScreenId, final Operation<Void> original) {
        if (Features.DIMENSION_CHANGE.isEnabled()) {
            BedrockDimensionChange.onChangeDimension(user, loadingScreenId);
        } else {
            original.call(user, type, loadingScreenId);
        }
    }

}
