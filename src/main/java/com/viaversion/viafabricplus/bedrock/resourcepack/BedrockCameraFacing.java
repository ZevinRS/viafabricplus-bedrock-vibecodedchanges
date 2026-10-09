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
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;

/**
 * Custom entities that turn toward the camera on Bedrock, like The Hive's game holograms in the hub. Their packs rotate
 * a bone by query.rotation_to_camera in an animation, which ViaBedrock doesn't play, so the models kept facing one way.
 * Java's display entities can do the same with their billboard mode.
 */
public final class BedrockCameraFacing {

    public static final byte FIXED = 0;
    public static final byte VERTICAL = 1;
    public static final byte HORIZONTAL = 2;
    public static final byte CENTER = 3;

    // The billboard mode of each entity identifier, for each pack stack
    private static final Map<ResourcePackStorage, Map<String, Byte>> MODES = Collections.synchronizedMap(new WeakHashMap<>());

    private BedrockCameraFacing() {
    }

    public static byte billboard(final ResourcePackStorage storage, final String identifier) {
        return MODES.computeIfAbsent(storage, BedrockCameraFacing::load).getOrDefault(identifier, FIXED);
    }

    private static Map<String, Byte> load(final ResourcePackStorage storage) {
        final Map<String, Byte> animationModes = new HashMap<>();
        final Map<String, Map<String, String>> entityAnimations = new HashMap<>();
        for (final ResourcePack pack : storage.getPackStackBottomToTop()) {
            for (final String path : pack.content().getFilesDeep("animations/", ".json")) {
                final JsonObject animations = object(read(pack, path), "animations");
                if (animations == null) {
                    continue;
                }
                for (final Map.Entry<String, JsonElement> animation : animations.entrySet()) {
                    animationModes.put(animation.getKey(), mode(object(animation.getValue(), "bones")));
                }
            }
            for (final String path : pack.content().getFilesDeep("entity/", ".json")) {
                final JsonObject description = object(object(read(pack, path), "minecraft:client_entity"), "description");
                if (description == null || !description.has("identifier")) {
                    continue;
                }
                final Map<String, String> animations = new HashMap<>();
                final JsonObject animationMap = object(description, "animations");
                if (animationMap != null) {
                    for (final Map.Entry<String, JsonElement> entry : animationMap.entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            animations.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                }
                entityAnimations.put(description.get("identifier").getAsString(), animations);
            }
        }

        final Map<String, Byte> modes = new HashMap<>();
        for (final Map.Entry<String, Map<String, String>> entity : entityAnimations.entrySet()) {
            for (final String animation : entity.getValue().values()) {
                final byte mode = animationModes.getOrDefault(animation, FIXED);
                if (mode != FIXED) {
                    modes.put(entity.getKey(), mode);
                    break;
                }
            }
        }
        return modes;
    }

    /**
     * @return how a bone of the animation turns toward the camera: query.rotation_to_camera(0) is the pitch and
     * query.rotation_to_camera(1) the yaw to it
     */
    private static byte mode(final JsonObject bones) {
        if (bones == null) {
            return FIXED;
        }
        for (final Map.Entry<String, JsonElement> bone : bones.entrySet()) {
            if (!bone.getValue().isJsonObject() || !(bone.getValue().getAsJsonObject().get("rotation") instanceof final JsonArray rotation) || rotation.size() < 2) {
                continue;
            }
            final boolean pitch = facesCamera(rotation.get(0), 0);
            final boolean yaw = facesCamera(rotation.get(1), 1);
            if (pitch && yaw) {
                return CENTER;
            } else if (yaw) {
                return VERTICAL;
            } else if (pitch) {
                return HORIZONTAL;
            }
        }
        return FIXED;
    }

    private static boolean facesCamera(final JsonElement expression, final int axis) {
        return expression.isJsonPrimitive() && expression.getAsJsonPrimitive().isString()
            && expression.getAsString().toLowerCase(Locale.ROOT).replace(" ", "").contains("rotation_to_camera(" + axis + ")");
    }

    private static JsonObject read(final ResourcePack pack, final String path) {
        try {
            return pack.content().getJson(path);
        } catch (final Exception e) { // Packs often have files that aren't valid JSON
            return null;
        }
    }

    private static JsonObject object(final JsonElement element, final String key) {
        return element != null && element.isJsonObject() && element.getAsJsonObject().get(key) instanceof final JsonObject object ? object : null;
    }

}
