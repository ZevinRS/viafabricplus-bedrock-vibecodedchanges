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

package com.viaversion.viafabricplus.bedrock.resourcepack;

import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.libs.gson.JsonPrimitive;

/**
 * Java only loads models whose parts are within -16 and 32 on every axis. The converter shrinks models to fit by an
 * estimate that some posed models still exceed, which Java then refused to load.
 */
public final class BedrockModelBounds {

    private static final float MIN = -16F;
    private static final float MAX = 32F;
    // The converter scales models around the Bedrock origin, which is here in Java's model space
    private static final float[] ORIGIN = {8F, 0F, 8F};

    private BedrockModelBounds() {
    }

    /**
     * Shrinks a converted model around the Bedrock origin until it fits.
     *
     * @return how much it was shrunk, 1 if it already fit
     */
    public static float fit(final JsonObject model) {
        if (!(model.get("elements") instanceof final JsonArray elements)) {
            return 1F;
        }
        float factor = 1F;
        for (final JsonElement element : elements) {
            if (element instanceof final JsonObject object) {
                factor = Math.min(factor, fitting(object.get("from")));
                factor = Math.min(factor, fitting(object.get("to")));
            }
        }
        if (factor >= 1F) {
            return 1F;
        }
        factor *= 0.999F; // Rounding stays inside
        for (final JsonElement element : elements) {
            if (element instanceof final JsonObject object) {
                scale(object.get("from"), factor);
                scale(object.get("to"), factor);
                if (object.get("rotation") instanceof final JsonObject rotation) {
                    scale(rotation.get("origin"), factor);
                }
            }
        }
        return factor;
    }

    private static float fitting(final JsonElement position) {
        float factor = 1F;
        if (position instanceof final JsonArray values && values.size() == 3) {
            for (int axis = 0; axis < 3; axis++) {
                final float offset = values.get(axis).getAsFloat() - ORIGIN[axis];
                if (offset > MAX - ORIGIN[axis]) {
                    factor = Math.min(factor, (MAX - ORIGIN[axis]) / offset);
                } else if (offset < MIN - ORIGIN[axis]) {
                    factor = Math.min(factor, (MIN - ORIGIN[axis]) / offset);
                }
            }
        }
        return factor;
    }

    private static void scale(final JsonElement position, final float factor) {
        if (position instanceof final JsonArray values && values.size() == 3) {
            for (int axis = 0; axis < 3; axis++) {
                values.set(axis, new JsonPrimitive((values.get(axis).getAsFloat() - ORIGIN[axis]) * factor + ORIGIN[axis]));
            }
        }
    }

}
