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

    // Ticks the player has been swimming for since it last started swimming, counting the tick it started in
    private static int ticksSinceSwimStart;

    // Whether the last move of the player didn't move it at all along the axis it mostly tried to move along
    private static boolean mainAxisBlocked;

    private BedrockSprint() {
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
