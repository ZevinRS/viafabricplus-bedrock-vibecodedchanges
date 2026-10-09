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

import com.viaversion.viafabricplus.bedrock.feature.Features;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

/**
 * The Bedrock client acts on some server updates of its own player later than Java, counted from the last
 * player_auth_input it sent before they arrived, as recorded on Dragonfly: a velocity the server sets is used from the
 * second move after that input on, and so are its entity flags (a sprinting flag the server sends back), and a movement
 * speed from the third. Java uses them for the next move. Those
 * packets are held back until the client sent the input before that move. Only used on the render thread.
 */
public final class BedrockPacketDelay {

    private static final Deque<Pending<?>> PENDING = new ArrayDeque<>();
    private static boolean applying;
    // Ticks the client player moved and sent its input in, counted on the render thread
    private static long sentTicks;

    private BedrockPacketDelay() {
    }

    /**
     * @return whether the packet was held back
     */
    public static <T extends Packet<?>> boolean hold(final T packet, final int entityId, final int moves, final BiConsumer<ClientPacketListener, T> handler) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || entityId != minecraft.player.getId()) {
            return false;
        }
        return hold(packet, moves, handler);
    }

    /**
     * Holds back a packet that isn't about an entity.
     *
     * @return whether the packet was held back
     */
    public static <T extends Packet<?>> boolean hold(final T packet, final int moves, final BiConsumer<ClientPacketListener, T> handler) {
        if (applying || Minecraft.getInstance().player == null || !Features.PACKET_DELAY.isEnabled()) {
            return false;
        }
        // Applied at the end of the tick that sends the input before the move it is used for
        PENDING.add(new Pending<>(packet, handler, sentTicks + moves - 1));
        if (BedrockInputReplay.isPlaying()) {
            ViaFabricPlusBedrock.impl().logger().info("[replay-packet] held {} after frame {} t={}", packet.getClass().getSimpleName(), BedrockInputReplay.frameIndex() - 1, System.currentTimeMillis());
        }
        return true;
    }

    /**
     * During a replay with the recorded velocities of the Bedrock run, the server's velocities for the player are ignored.
     *
     * @return whether the velocity is ignored
     */
    public static boolean ignoreMotion(final int entityId) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (applying || minecraft.player == null || entityId != minecraft.player.getId() || !BedrockInputReplay.usesRecordedMotion()) {
            return false;
        }
        ViaFabricPlusBedrock.impl().logger().info("[replay-packet] ignored the server's motion after frame {}", BedrockInputReplay.frameIndex() - 1);
        return true;
    }

    /**
     * Called at the end of every client tick, after the tick's input was sent.
     */
    public static void tick(final Minecraft minecraft) {
        if (minecraft.player != null) {
            sentTicks++;
            final Vec3 recordedMotion = BedrockInputReplay.pollRecordedMotion();
            if (recordedMotion != null && minecraft.getConnection() != null) {
                ViaFabricPlusBedrock.impl().logger().info("[replay-packet] applied recorded motion after frame {}", BedrockInputReplay.frameIndex() - 1);
                applying = true;
                try {
                    minecraft.getConnection().handleSetEntityMotion(new ClientboundSetEntityMotionPacket(minecraft.player.getId(), recordedMotion));
                } finally {
                    applying = false;
                }
            }
        }
        if (PENDING.isEmpty()) {
            return;
        }
        final ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null) {
            PENDING.clear();
            return;
        }
        // Each kind of update has its own delay, so a later packet can be due before an earlier one. Turning the delay
        // off applies the held packets right away.
        final boolean delaying = Features.PACKET_DELAY.isEnabled();
        final List<Pending<?>> due = new ArrayList<>();
        PENDING.removeIf(pending -> (!delaying || pending.dueTick <= sentTicks) && due.add(pending));
        applying = true;
        try {
            for (final Pending<?> pending : due) {
                if (BedrockInputReplay.isPlaying()) {
                    ViaFabricPlusBedrock.impl().logger().info("[replay-packet] applied {} after frame {}", pending.packet().getClass().getSimpleName(), BedrockInputReplay.frameIndex() - 1);
                }
                pending.apply(connection);
            }
        } finally {
            applying = false;
        }
    }

    private record Pending<T extends Packet<?>>(T packet, BiConsumer<ClientPacketListener, T> handler, long dueTick) {

        void apply(final ClientPacketListener connection) {
            this.handler.accept(connection, this.packet);
        }

    }

}
