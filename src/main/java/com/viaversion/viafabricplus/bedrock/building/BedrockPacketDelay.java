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
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;

/**
 * The Bedrock client acts on some server updates of its own player later than Java, as recorded on Dragonfly: the
 * movement speed it sends right after a sprint stop still applies to the next two moves. Those packets are held back
 * for that many client ticks. Only used on the render thread.
 */
public final class BedrockPacketDelay {

    public static final int ATTRIBUTE_TICKS = 2;

    private static final Deque<Pending<?>> PENDING = new ArrayDeque<>();
    private static boolean applying;

    private BedrockPacketDelay() {
    }

    /**
     * @return whether the packet was held back
     */
    public static <T extends Packet<?>> boolean hold(final T packet, final int entityId, final int ticks, final BiConsumer<ClientPacketListener, T> handler) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (applying || minecraft.player == null || entityId != minecraft.player.getId()) {
            return false;
        }
        PENDING.add(new Pending<>(packet, handler, new int[]{ticks}));
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
        // Packets are applied in the order they arrived, so one is only due when all before it are
        final List<Pending<?>> due = new ArrayList<>();
        for (final Pending<?> pending : PENDING) {
            pending.ticksLeft[0]--;
        }
        while (!PENDING.isEmpty() && PENDING.peek().ticksLeft[0] <= 0) {
            due.add(PENDING.poll());
        }
        applying = true;
        try {
            for (final Pending<?> pending : due) {
                pending.apply(connection);
            }
        } finally {
            applying = false;
        }
    }

    private record Pending<T extends Packet<?>>(T packet, BiConsumer<ClientPacketListener, T> handler, int[] ticksLeft) {

        void apply(final ClientPacketListener connection) {
            this.handler.accept(connection, this.packet);
        }

    }

}
