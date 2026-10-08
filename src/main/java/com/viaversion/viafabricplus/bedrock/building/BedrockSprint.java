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

import net.minecraft.resources.Identifier;

/**
 * State for Bedrock's sprint rules (SprintTriggerSystem::doIntentTick), only used on the render thread.
 */
public final class BedrockSprint {

    // The part of the server's movement speed its modifiers don't explain, which Bedrock forgets when its sprint modifier changes
    public static final Identifier SERVER_VALUE_MODIFIER = Identifier.fromNamespaceAndPath("viafabricplus_bedrock", "server_value");

    // Whether the player was in water in the previous tick
    private static boolean inWaterLastTick;

    // Whether the player started and stopped sprinting during the current tick
    private static boolean startedThisTick;
    private static boolean stoppedThisTick;

    // Ticks the player has been swimming for since it last started swimming, counting the tick it started in
    private static int ticksSinceSwimStart;

    // Whether the last move of the player didn't move it at all along the axis it mostly tried to move along
    private static boolean mainAxisBlocked;

    private BedrockSprint() {
    }

    /**
     * Called before the player ticks, when the water state is still the one of the previous tick.
     */
    public static void onTickStart(final boolean inWater) {
        startedThisTick = stoppedThisTick = false;
        inWaterLastTick = inWater;
    }

    public static boolean wasInWaterLastTick() {
        return inWaterLastTick;
    }

    public static void onSprintChange(final boolean sprinting) {
        if (sprinting) {
            startedThisTick = true;
        } else {
            stoppedThisTick = true;
        }
    }

    /**
     * Java only tells the server about a change of the sprinting it has at the end of the tick, compared to what it
     * last told it. Bedrock sends a start and a stop for every start and stop during the tick, for example both every
     * tick it keeps trying to sprint into a wall, and a start again when the server stopped the sprinting.
     */
    public static boolean startedThisTick() {
        return startedThisTick;
    }

    public static boolean stoppedThisTick() {
        return stoppedThisTick;
    }

    public static void onSwimStart() {
        ticksSinceSwimStart = 0;
    }

    /**
     * Called once per tick before the player moves.
     */
    public static int tickSwimming(final boolean swimming) {
        ticksSinceSwimStart = swimming ? ticksSinceSwimStart + 1 : 0;
        return ticksSinceSwimStart;
    }

    public static boolean isMainAxisBlocked() {
        return mainAxisBlocked;
    }

    public static void setMainAxisBlocked(final boolean blocked) {
        mainAxisBlocked = blocked;
    }

}
