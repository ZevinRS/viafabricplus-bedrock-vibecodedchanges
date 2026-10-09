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
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

/**
 * Flat parts of entity models, like the walls of The Hive's SkyWars border, often have their texture on one side only.
 * Bedrock's usual entity materials draw faces from both directions, so they show from either side, but Java only
 * draws a face from the front, which hid every wall facing away. The side without texture gets the other side's
 * texture, mirrored the way it's seen from behind.
 */
public final class BedrockDoubleSidedPlanes {

    // The two faces of a flat element along x, y and z
    private static final String[][] FACE_PAIRS = {{"west", "east"}, {"down", "up"}, {"north", "south"}};

    private BedrockDoubleSidedPlanes() {
    }

    /**
     * @param texture the model's texture, only decoded if the model has flat parts
     */
    public static void apply(final JsonObject model, final Supplier<BufferedImage> texture) {
        if (!(model.get("elements") instanceof final JsonArray elements) || !hasFlatParts(elements)) {
            return;
        }
        final BufferedImage image = texture.get();
        if (image == null) {
            return;
        }
        for (final JsonElement element : elements) {
            if (!(element instanceof final JsonObject object) || !(object.get("from") instanceof final JsonArray from) || !(object.get("to") instanceof final JsonArray to)
                || !(object.get("faces") instanceof final JsonObject faces)) {
                continue;
            }
            for (int axis = 0; axis < 3; axis++) {
                if (from.get(axis).getAsFloat() != to.get(axis).getAsFloat()) {
                    continue;
                }
                final JsonObject first = faces.get(FACE_PAIRS[axis][0]) instanceof final JsonObject face ? face : null;
                final JsonObject second = faces.get(FACE_PAIRS[axis][1]) instanceof final JsonObject face ? face : null;
                if (first == null || second == null) {
                    continue;
                }
                final boolean firstShown = isShown(first, image);
                final boolean secondShown = isShown(second, image);
                if (firstShown && !secondShown) {
                    second.add("uv", mirrored(first.getAsJsonArray("uv")));
                } else if (secondShown && !firstShown) {
                    first.add("uv", mirrored(second.getAsJsonArray("uv")));
                }
            }
        }
    }

    /**
     * Bedrock draws blocks with see-through materials from both sides, but the flat parts of their geometries, like
     * the planes of flowers, only have the face on one side. Adds the face on the other side, with the texture as it's
     * seen from behind.
     */
    public static void addBackFaces(final JsonObject model) {
        if (!(model.get("elements") instanceof final JsonArray elements)) {
            return;
        }
        for (final JsonElement element : elements) {
            if (!(element instanceof final JsonObject object) || !(object.get("from") instanceof final JsonArray from) || !(object.get("to") instanceof final JsonArray to)
                || !(object.get("faces") instanceof final JsonObject faces)) {
                continue;
            }
            for (int axis = 0; axis < 3; axis++) {
                if (from.get(axis).getAsFloat() != to.get(axis).getAsFloat()) {
                    continue;
                }
                final String first = FACE_PAIRS[axis][0];
                final String second = FACE_PAIRS[axis][1];
                if (faces.get(first) instanceof final JsonObject face && !faces.has(second)) {
                    faces.add(second, back(face, axis));
                } else if (faces.get(second) instanceof final JsonObject face && !faces.has(first)) {
                    faces.add(first, back(face, axis));
                }
            }
        }
    }

    private static JsonObject back(final JsonObject face, final int axis) {
        final JsonObject back = face.deepCopy();
        back.remove("cullface");
        if (face.get("uv") instanceof final JsonArray uv && uv.size() == 4) {
            // The texture of up and down runs the other way along z, of the sides the other way along their width
            back.add("uv", axis == 1 ? flipped(uv) : mirrored(uv));
        }
        return back;
    }

    private static JsonArray flipped(final JsonArray uv) {
        final JsonArray flipped = new JsonArray();
        flipped.add(uv.get(0));
        flipped.add(uv.get(3));
        flipped.add(uv.get(2));
        flipped.add(uv.get(1));
        return flipped;
    }

    private static boolean hasFlatParts(final JsonArray elements) {
        for (final JsonElement element : elements) {
            if (element instanceof final JsonObject object && object.get("from") instanceof final JsonArray from && object.get("to") instanceof final JsonArray to) {
                for (int axis = 0; axis < 3; axis++) {
                    if (from.get(axis).getAsFloat() == to.get(axis).getAsFloat()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @return whether any pixel of the face's texture area is visible
     */
    private static boolean isShown(final JsonObject face, final BufferedImage texture) {
        if (!(face.get("uv") instanceof final JsonArray uv) || uv.size() < 4) {
            return false;
        }
        // UVs go from 0 to 16 over the whole texture
        final float scaleX = texture.getWidth() / 16F;
        final float scaleY = texture.getHeight() / 16F;
        final int x0 = clamp(Math.min(uv.get(0).getAsFloat(), uv.get(2).getAsFloat()) * scaleX, texture.getWidth());
        final int x1 = clamp(Math.max(uv.get(0).getAsFloat(), uv.get(2).getAsFloat()) * scaleX, texture.getWidth());
        final int y0 = clamp(Math.min(uv.get(1).getAsFloat(), uv.get(3).getAsFloat()) * scaleY, texture.getHeight());
        final int y1 = clamp(Math.max(uv.get(1).getAsFloat(), uv.get(3).getAsFloat()) * scaleY, texture.getHeight());
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if ((texture.getRGB(x, y) >>> 24) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int clamp(final float value, final int max) {
        return Math.max(0, Math.min(max, Math.round(value)));
    }

    private static JsonArray mirrored(final JsonArray uv) {
        final JsonArray mirrored = new JsonArray();
        mirrored.add(uv.get(2));
        mirrored.add(uv.get(1));
        mirrored.add(uv.get(0));
        mirrored.add(uv.get(3));
        return mirrored;
    }

}
