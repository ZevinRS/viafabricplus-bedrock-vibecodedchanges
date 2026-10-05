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
import com.viaversion.viaversion.api.minecraft.Vector3d;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.api.model.entity.Entity;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.ClientboundBedrockPackets;
import net.raphimc.viabedrock.protocol.model.Position3f;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import net.raphimc.viabedrock.protocol.types.BedrockTypes;

/**
 * Servers with server authoritative movement and rewind (like BDS) send the player's motion with the tick it started
 * at. The Bedrock client applies it at that tick and replays the ticks since, as recorded: the motion of tick T shows up
 * in the client's next position as all movement from tick T on. Java applies motion when it arrives, which puts the
 * player a few ticks of movement behind where the server expects, so the missing ticks are replayed right away.
 */
public final class BedrockKnockback {

    // The tick each motion of the client player started at, 0 if the server doesn't say, in the order they arrive
    private static final Queue<Long> MOTION_TICKS = new ConcurrentLinkedQueue<>();
    private static final int MAX_REPLAYED_TICKS = 20;

    private BedrockKnockback() {
    }

    public static void register(final BedrockProtocol protocol) {
        // ViaBedrock's translation, which drops the tick
        protocol.replaceClientbound(ClientboundBedrockPackets.SET_ENTITY_MOTION, wrapper -> {
            final EntityTracker entityTracker = wrapper.user().get(EntityTracker.class);

            final long entityRuntimeId = wrapper.read(BedrockTypes.UNSIGNED_VAR_LONG); // entity runtime id
            final Position3f motion = wrapper.read(BedrockTypes.POSITION_3F); // motion
            final long tick = wrapper.read(BedrockTypes.UNSIGNED_VAR_LONG); // tick

            final Entity entity = entityTracker.getEntityByRid(entityRuntimeId);
            if (entity == null) {
                wrapper.cancel();
                return;
            }
            if (entity instanceof ClientPlayerEntity) {
                MOTION_TICKS.add(tick);
            }

            wrapper.write(Types.VAR_INT, entity.javaId()); // entity id
            wrapper.write(Types.LOW_PRECISION_VECTOR, new Vector3d(motion.x(), motion.y(), motion.z())); // velocity
        });
    }

    /**
     * Called after the client applied a motion of its own player.
     */
    public static void onMotionApplied(final LocalPlayer player) {
        final Long tick = MOTION_TICKS.poll();
        if (tick == null || tick <= 0 || !BedrockBuilding.isActive()) {
            return;
        }
        final UserConnection user = ViaFabricPlus.api().userConnection();
        if (user == null) {
            return;
        }
        // The last sent tick, the next movement is of the tick after it
        final long lastTick = user.get(EntityTracker.class).getClientPlayer().age();
        final long missedTicks = lastTick - tick;
        if (missedTicks <= 0 || missedTicks > MAX_REPLAYED_TICKS) {
            return;
        }
        for (int i = 0; i < missedTicks; i++) {
            player.travel(new Vec3(player.xxa, player.yya, player.zza));
        }
    }

    public static void clear() {
        MOTION_TICKS.clear();
    }

}
