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
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.api.model.container.player.OffhandContainer;
import net.raphimc.viabedrock.protocol.model.BedrockItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OffhandContainer.class, remap = false)
public abstract class MixinOffhandContainer extends Container {

    private MixinOffhandContainer(final UserConnection user) {
        super(user, (byte) 0, null, null, null, 0);
    }

    /**
     * Like {@link MixinInventoryContainer}, only send the offhand item when it changed.
     */
    @Inject(method = "onSlotChanged", at = @At("HEAD"), cancellable = true)
    private void sendOnlyChangedOffhandItem(final int slot, final BedrockItem oldItem, final BedrockItem newItem, final CallbackInfo ci) {
        if (slot == 0 && !BedrockPlacementTranslator.state(this.user).equipOffhand(newItem)) {
            ci.cancel();
        }
    }

}
