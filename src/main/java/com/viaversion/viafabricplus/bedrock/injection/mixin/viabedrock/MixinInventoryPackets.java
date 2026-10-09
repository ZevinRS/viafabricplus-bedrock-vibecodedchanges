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
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.libs.mcstructs.text.TextComponent;
import net.raphimc.viabedrock.api.model.container.ChestContainer;
import net.raphimc.viabedrock.protocol.packet.InventoryPackets;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Containers of entities, like the chests The Hive drops for dead players, come without a block position. ViaBedrock
 * opened them at 0 0 0 and closed them again in the next tick, since no chest is there.
 */
@Mixin(value = InventoryPackets.class, remap = false)
public abstract class MixinInventoryPackets {

    @Unique
    private static boolean viaFabricPlusBedrock$entityContainer;

    @WrapOperation(method = "lambda$register$0", at = @At(value = "INVOKE", target = "Lcom/viaversion/viaversion/api/protocol/packet/PacketWrapper;read(Lcom/viaversion/viaversion/api/type/Type;)Ljava/lang/Object;"))
    private static Object readEntityId(final PacketWrapper wrapper, final Type<?> type, final Operation<Object> original) {
        final Object value = original.call(wrapper, type);
        if ((Object) type == BedrockTypes.VAR_LONG) { // The entity the container belongs to, -1 for blocks
            viaFabricPlusBedrock$entityContainer = (Long) value != -1L;
        }
        return value;
    }

    @WrapOperation(method = "lambda$register$0", at = @At(value = "NEW", target = "net/raphimc/viabedrock/api/model/container/ChestContainer"))
    private static ChestContainer openWithoutBlock(final UserConnection user, final byte containerId, final TextComponent title, final BlockPosition position, final int size,
                                                   final Operation<ChestContainer> original) {
        // Without a position the container isn't closed for missing its block or being too far from it
        return original.call(user, containerId, title, viaFabricPlusBedrock$entityContainer ? null : position, size);
    }

}
