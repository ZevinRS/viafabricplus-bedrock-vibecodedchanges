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

import com.viaversion.viafabricplus.bedrock.inventory.BedrockInventoryTranslator;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import java.util.List;
import net.raphimc.viabedrock.api.model.entity.Entity;
import net.raphimc.viabedrock.protocol.data.enums.java.generated.PlayerActionAction;
import net.raphimc.viabedrock.protocol.model.EntityLink;
import net.raphimc.viabedrock.protocol.packet.InteractionPackets;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InteractionPackets.class, remap = false)
public abstract class MixinInteractionPackets {

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private static void dropHeldItem(final PacketWrapper wrapper, final PlayerActionAction action, final CallbackInfoReturnable<Boolean> cir) {
        if ((action == PlayerActionAction.DROP_ITEM || action == PlayerActionAction.DROP_ALL_ITEMS)
            && BedrockInventoryTranslator.dropHeldItem(wrapper.user(), action == PlayerActionAction.DROP_ALL_ITEMS)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * ViaBedrock keeps passengers that were removed and looked up entities of links without checking them, which
     * disconnected with an exception when The Hive linked entities in its hub.
     */
    @Inject(method = "lambda$register$3", at = @At("HEAD"), cancellable = true)
    private static void skipLinksToUnknownEntities(final PacketWrapper wrapper, final CallbackInfo ci) {
        final EntityLink link = wrapper.read(BedrockTypes.ENTITY_LINK);
        wrapper.resetReader();
        final EntityTracker entityTracker = wrapper.user().get(EntityTracker.class);
        final Entity vehicle = entityTracker.getEntityByUid(link.fromEntityUniqueId());
        if (vehicle == null || entityTracker.getEntityByUid(link.toEntityUniqueId()) == null) {
            wrapper.cancel();
            ci.cancel();
            return;
        }
        for (final long passenger : List.copyOf(vehicle.passengers())) {
            if (entityTracker.getEntityByUid(passenger) == null) {
                vehicle.removePassenger(passenger);
            }
        }
    }

}
