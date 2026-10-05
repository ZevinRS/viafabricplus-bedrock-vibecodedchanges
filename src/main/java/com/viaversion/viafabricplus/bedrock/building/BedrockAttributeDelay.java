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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;

/**
 * The Bedrock client moves with attributes the server changed only from the second tick after it received them on, as
 * recorded on Dragonfly: the movement speed it sends right after a sprint stop still applies to the next two moves. Java
 * applies them before the next move, so the local player's attribute updates are held back for two client ticks. Only
 * used on the render thread.
 */
public final class BedrockAttributeDelay {

    private static final int DELAY_TICKS = 2;

    private static final Deque<Pending> PENDING = new ArrayDeque<>();
    private static boolean applying;

    private BedrockAttributeDelay() {
    }

    /**
     * @return whether the packet was held back
     */
    public static boolean hold(final ClientboundUpdateAttributesPacket packet) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (applying || minecraft.player == null || packet.getEntityId() != minecraft.player.getId()) {
            return false;
        }
        PENDING.add(new Pending(packet, new int[]{DELAY_TICKS}));
        return true;
    }

    /**
     * Called at the end of every client tick.
     */
    public static void tick(final Minecraft minecraft) {
        if (PENDING.isEmpty()) {
            return;
        }
        final ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null) {
            PENDING.clear();
            return;
        }
        final List<ClientboundUpdateAttributesPacket> due = new ArrayList<>();
        for (final Pending pending : PENDING) {
            pending.ticksLeft[0]--;
        }
        while (!PENDING.isEmpty() && PENDING.peek().ticksLeft[0] <= 0) {
            due.add(PENDING.poll().packet);
        }
        applying = true;
        try {
            for (final ClientboundUpdateAttributesPacket packet : due) {
                connection.handleUpdateAttributes(packet);
            }
        } finally {
            applying = false;
        }
    }

    private record Pending(ClientboundUpdateAttributesPacket packet, int[] ticksLeft) {
    }

}
