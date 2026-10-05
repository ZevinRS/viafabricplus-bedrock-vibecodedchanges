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

import com.viaversion.viafabricplus.bedrock.building.BedrockPlacementTranslator;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.api.model.container.player.InventoryContainer;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InventoryContainer.class, remap = false)
public abstract class MixinInventoryContainer extends Container {

    @Shadow
    private byte selectedHotbarSlot;

    private MixinInventoryContainer(final UserConnection user) {
        super(user, (byte) 0, null, null, null, 0);
    }

    /**
     * ViaBedrock sends the held item on every server update of the selected slot, but the Bedrock client only sends it
     * when the held item changed, for example not when the server confirms the count of a placement.
     */
    @Inject(method = "onSlotChanged", at = @At("HEAD"), cancellable = true)
    private void sendOnlyChangedHeldItem(final int slot, final BedrockItem oldItem, final BedrockItem newItem, final CallbackInfo ci) {
        if (slot == this.selectedHotbarSlot && !BedrockPlacementTranslator.state(this.user).equip(slot, newItem)) {
            ci.cancel();
        }
    }

    @Inject(method = "setSelectedHotbarSlot", at = @At("TAIL"))
    private void rememberHeldItem(final byte slot, final PacketWrapper mobEquipment, final CallbackInfo ci) {
        BedrockPlacementTranslator.state(this.user).equip(slot, this.getItem(slot));
    }

}
