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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.entity;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.render.BedrockSkins;
import com.mojang.authlib.GameProfile;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives Bedrock players their skin, see {@link BedrockSkins}. The player list and the players in it ask the entry for their skin.
 */
@Mixin(PlayerInfo.class)
public abstract class MixinPlayerInfo {

    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void useBedrockSkin(final CallbackInfoReturnable<PlayerSkin> cir) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            final BedrockSkins.Skin skin = BedrockSkins.get(this.getProfile().id());
            final PlayerSkin javaSkin = skin != null ? skin.javaSkin() : null;
            if (javaSkin != null) {
                cir.setReturnValue(javaSkin);
            }
        }
    }

}
