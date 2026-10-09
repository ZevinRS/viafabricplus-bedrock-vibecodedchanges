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

package com.viaversion.viafabricplus.bedrock.building;

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viaversion.api.connection.UserConnection;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.ActorFlags;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;

/**
 * The immobile flag a server can give the player (ActorFlags.NOAI, bit 16), which ViaBedrock had no Java equivalent
 * for. While it's set, Bedrock doesn't move the player at all, not even by gravity, and clears its velocity and jump
 * (ImmobileSystem, MoveSpeedCapSystem). The Hive sets it when you join a game. Only used on the render thread.
 */
public final class BedrockImmobile {

    private static boolean immobile;

    private BedrockImmobile() {
    }

    public static boolean isImmobile() {
        return immobile;
    }

    // The Bedrock client doesn't move in the tick it handles a teleport from the server, so the input confirming it
    // has the position the server sent, as recorded on The Hive. The server ignored the player after a confirmation
    // from elsewhere.
    private static boolean teleported;

    public static void onTeleported() {
        teleported = true;
    }

    /**
     * @return whether the player was teleported since its last move
     */
    public static boolean takeTeleported() {
        final boolean wasTeleported = teleported;
        teleported = false;
        return wasTeleported;
    }

    public static void setImmobile(final boolean immobile) {
        BedrockImmobile.immobile = immobile;
    }

    /**
     * @return whether the entity flags the server last sent for the player hold the immobile flag
     */
    public static boolean serverFlag() {
        final UserConnection user = ViaFabricPlus.api().userConnection();
        final EntityTracker tracker = user != null ? user.get(EntityTracker.class) : null;
        final ClientPlayerEntity player = tracker != null ? tracker.getClientPlayer() : null;
        return player != null && player.entityFlags().contains(ActorFlags.NOAI);
    }

}
