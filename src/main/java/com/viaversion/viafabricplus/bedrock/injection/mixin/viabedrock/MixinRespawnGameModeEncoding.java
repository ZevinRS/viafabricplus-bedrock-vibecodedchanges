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
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.api.type.Types;
import net.raphimc.viabedrock.protocol.packet.ClientPlayerPackets;
import net.raphimc.viabedrock.protocol.packet.WorldPackets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = {WorldPackets.class, ClientPlayerPackets.class}, remap = false)
public abstract class MixinRespawnGameModeEncoding {

    /**
     * Java 26.3 reads the previous game mode of the respawn packet as an optional VarInt, but the bundled ViaBedrock
     * still writes a -1 byte (fixed upstream in 2fb3559, not published yet). 0xFF is read as a VarInt continuation,
     * shifting every later field by a byte, so dimension changes and respawns fail to decode and disconnect.
     * The selector matches all methods since the packet is written in lambdas.
     */
    @WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;write(Lcom/viaversion/viaversion/api/type/Type;Ljava/lang/Object;)V"))
    private static void writeEmptyPreviousGameMode(final PacketWrapper wrapper, final Type<?> type, final Object value, final Operation<Void> original) {
        if (type == Types.BYTE && value instanceof final Byte b && b == -1) {
            original.call(wrapper, Types.OPTIONAL_VAR_INT, null);
        } else {
            original.call(wrapper, type, value);
        }
    }

}
