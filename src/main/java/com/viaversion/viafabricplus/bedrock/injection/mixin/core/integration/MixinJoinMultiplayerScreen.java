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

package com.viaversion.viafabricplus.bedrock.injection.mixin.core.integration;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.injection.access.IServerAddress;
import com.viaversion.viafabricplus.bedrock.screen.BedrockAccountsScreen;
import com.viaversion.viafabricplus.bedrock.protocoltranslator.network.NetherNetAddressParser;
import com.viaversion.viafabricplus.bedrock.settings.BedrockSettings;
import com.viaversion.viafabricplus.injection.access.core.IServerData;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.net.SocketAddress;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class MixinJoinMultiplayerScreen extends Screen {

    @Unique
    private Button viaFabricPlusBedrock$accountsButton;

    public MixinJoinMultiplayerScreen(final Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addAccountsButton(final CallbackInfo ci) {
        final String name = ViaFabricPlusBedrock.impl().account().displayName();
        this.viaFabricPlusBedrock$accountsButton = Button.builder(BedrockAccountsScreen.TITLE,
                _ -> new BedrockAccountsScreen().open(this))
            .tooltip(Tooltip.create(name != null ? Component.translatable("bedrock_accounts.viafabricplus.current", name)
                : Component.translatable("bedrock_accounts.viafabricplus.none")))
            .size(98, 20).build();
        this.addRenderableWidget(this.viaFabricPlusBedrock$accountsButton);
        this.viaFabricPlusBedrock$placeAccountsButton(ci);
    }

    // Across from the ViaFabricPlus button, which is in the top left corner by default
    @Inject(method = "repositionElements", at = @At("RETURN"))
    private void viaFabricPlusBedrock$placeAccountsButton(final CallbackInfo ci) {
        if (this.viaFabricPlusBedrock$accountsButton != null) {
            this.viaFabricPlusBedrock$accountsButton.setPosition(this.width - 98 - 5, 5);
        }
    }

    @WrapOperation(method = "join(Lnet/minecraft/client/multiplayer/ServerData;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/resolver/ServerAddress;parseString(Ljava/lang/String;)Lnet/minecraft/client/multiplayer/resolver/ServerAddress;"))
    private ServerAddress replaceDefaultPort(final String input, final Operation<ServerAddress> original, @Local(argsOnly = true) final ServerData data) {
        final IServerData mixinServerInfo = (IServerData) data;

        final ProtocolVersion version;
        if (mixinServerInfo.viaFabricPlus$passedDirectConnectScreen()) {
            version = ViaFabricPlus.api().targetVersion();
        } else {
            version = mixinServerInfo.viaFabricPlus$forcedVersion();
        }
        if (BedrockProtocolVersion.BEDROCK_LATEST.equals(version)) {
            final SocketAddress netherNetAddress = NetherNetAddressParser.parse(input);
            if (netherNetAddress != null) {
                final ServerAddress address = original.call("nethernet.viafabricplus.localhost");
                ((IServerAddress) (Object) address).viaFabricPlusBedrock$setNetherNetAddress(netherNetAddress);
                return address;
            }
        }
        return original.call(ViaFabricPlusBedrock.impl().settings().replaceDefaultPort(input, version));
    }

}
