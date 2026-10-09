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

import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.protocol.model.Position3f;

/**
 * Values of the local player that ViaBedrock writes into the player_auth_input packet. They are set on the render
 * thread right before the movement packet is sent, and read while that packet is translated.
 */
public final class BedrockAuthInput {

    private static volatile Position3f velocity;

    private BedrockAuthInput() {
    }

    /**
     * The velocity the player starts the next tick with, which is what Bedrock sends as delta. ViaBedrock estimates it
     * from the position change instead, with air and ground friction only.
     */
    public static void setVelocity(final Vec3 velocity) {
        BedrockAuthInput.velocity = new Position3f((float) velocity.x, (float) velocity.y, (float) velocity.z);
    }

    public static Position3f velocity() {
        return velocity;
    }

    // Whether the player jumped since the last input, set on the render thread
    private static volatile boolean jumped;

    public static void onJump() {
        jumped = true;
    }

    /**
     * The Bedrock client only says it starts jumping in the tick it jumps, as recorded on The Hive. ViaBedrock said so
     * whenever the jump key was held on the ground, also while Java waits between jumps with the key held.
     */
    public static boolean takeJumped() {
        final boolean wasJumped = jumped;
        jumped = false;
        return wasJumped;
    }

}
