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

package com.viaversion.viafabricplus.bedrock.feature;

import com.viaversion.viafabricplus.bedrock.injection.mixin.viabedrock.MixinAbstractProtocol;
import com.viaversion.viaversion.api.protocol.packet.PacketType;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMapping;
import com.viaversion.viaversion.api.protocol.packet.mapping.PacketMappings;
import com.viaversion.viaversion.api.protocol.remapper.PacketHandler;
import com.viaversion.viaversion.protocols.v26_2to26_3.packet.ServerboundPackets26_3;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces ViaBedrock's translation of a packet with the mod's while a feature is on, and keeps ViaBedrock's for when
 * it's off.
 */
public final class SwitchableHandlers {

    private SwitchableHandlers() {
    }

    public static void replaceServerbound(final BedrockProtocol protocol, final ServerboundPackets26_3 type, final FeatureImpl feature, final PacketHandler handler) {
        final PacketHandler original = original(((MixinAbstractProtocol) protocol).viaFabricPlusBedrock$getServerboundMappings(), type);
        protocol.replaceServerbound(type, switching(feature, handler, original));
    }

    public static void replaceClientbound(final BedrockProtocol protocol, final ClientboundBedrockPackets type, final FeatureImpl feature, final PacketHandler handler) {
        final PacketHandler original = original(((MixinAbstractProtocol) protocol).viaFabricPlusBedrock$getClientboundMappings(), type);
        protocol.replaceClientbound(type, switching(feature, handler, original));
    }

    private static @Nullable PacketHandler original(final PacketMappings mappings, final PacketType type) {
        final PacketMapping mapping = mappings.mappedPacket(type.state(), type.getId());
        return mapping != null ? mapping.handler() : null;
    }

    private static PacketHandler switching(final FeatureImpl feature, final PacketHandler handler, final @Nullable PacketHandler original) {
        return wrapper -> {
            if (feature.isEnabled()) {
                handler.handle(wrapper);
            } else if (original != null) {
                original.handle(wrapper);
            }
        };
    }

}
