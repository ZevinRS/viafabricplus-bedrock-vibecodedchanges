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

import com.viaversion.viaversion.api.type.Type;
import net.raphimc.viabedrock.protocol.storage.ResourcePackLoadStateTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ResourcePackLoadStateTracker.class, remap = false)
public abstract class MixinResourcePackLoadStateTracker {

    /**
     * Since Bedrock 1.26.40 the pack list of the "downloading" resource pack client response has a VarInt length,
     * but ViaBedrock still writes a little endian short. Servers then fail to read the packet and close the connection.
     * The field is only read when writing that list; the selector matches all methods since it is read in a lambda.
     */
    @Redirect(method = "*", at = @At(value = "FIELD", target = "Lnet/raphimc/viabedrock/protocol/types/BedrockTypes;SHORT_LE_STRING_ARRAY:Lcom/viaversion/viaversion/api/type/Type;", opcode = Opcodes.GETSTATIC))
    private Type<String[]> useVarIntPackList() {
        return BedrockTypes.STRING_ARRAY;
    }

}
