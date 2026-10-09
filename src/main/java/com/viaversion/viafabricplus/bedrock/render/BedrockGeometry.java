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

package com.viaversion.viafabricplus.bedrock.render;

import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jetbrains.annotations.Nullable;

/**
 * A Bedrock geometry as its file has it, in Bedrock's model space: y up, 16 units a block. Both file formats are read:
 * format 1.12 and later with a list of geometries, and the older one with one key per geometry, which can name a
 * geometry it extends after a colon.
 *
 * @param bones in file order, parents not necessarily first
 */
public record BedrockGeometry(String identifier, float textureWidth, float textureHeight, List<Bone> bones) {

    /**
     * @param rotation degrees around x, y and z
     */
    public record Bone(String name, @Nullable String parent, float[] pivot, float[] rotation, List<Cube> cubes) {
    }

    /**
     * @param uv      the corner of the box layout, or null if every face has its own area
     * @param faceUvs by Bedrock face name: the corner and size of its area, sizes can be negative to flip it
     */
    public record Cube(float[] origin, float[] size, float inflate, boolean mirror, float @Nullable [] pivot, float @Nullable [] rotation,
                       float @Nullable [] uv, Map<String, float[]> faceUvs) {
    }

    /**
     * @return every geometry of a geometry file by identifier
     */
    public static Map<String, BedrockGeometry> parseFile(final JsonObject file) {
        return parseFile(file, name -> null);
    }

    /**
     * @param externalParents geometries in the older format that the file's can extend without having them, like
     *                        skins extending Bedrock's own humanoid geometry
     * @return every geometry of a geometry file by identifier
     */
    public static Map<String, BedrockGeometry> parseFile(final JsonObject file, final Function<String, @Nullable JsonObject> externalParents) {
        final Map<String, BedrockGeometry> geometries = new LinkedHashMap<>();
        if (file.get("minecraft:geometry") instanceof final JsonArray list) {
            for (final JsonElement element : list) {
                if (element instanceof final JsonObject geometry && geometry.get("description") instanceof final JsonObject description
                    && description.get("identifier") instanceof final JsonElement identifier) {
                    geometries.put(identifier.getAsString(), parse(identifier.getAsString(), description, geometry, null));
                }
            }
            return geometries;
        }

        // Older format: "geometry.name" or "geometry.name:geometry.parent" keys
        final Map<String, String> parents = new HashMap<>();
        final Map<String, JsonObject> objects = new LinkedHashMap<>();
        for (final Map.Entry<String, JsonElement> entry : file.entrySet()) {
            if (!entry.getKey().startsWith("geometry.") || !(entry.getValue() instanceof final JsonObject geometry)) {
                continue;
            }
            final String[] names = entry.getKey().split(":", 2);
            objects.put(names[0], geometry);
            if (names.length > 1) {
                parents.put(names[0], names[1]);
            }
        }
        for (final Map.Entry<String, JsonObject> entry : objects.entrySet()) {
            final String parentName = parents.get(entry.getKey());
            final JsonObject parent = parentName == null ? null : objects.containsKey(parentName) ? objects.get(parentName) : externalParents.apply(parentName);
            geometries.put(entry.getKey(), parse(entry.getKey(), entry.getValue(), entry.getValue(), parent));
        }
        return geometries;
    }

    /**
     * @param description where the texture size is, the geometry itself in the older format
     * @param parent      the geometry this one extends in the older format, whose bones it replaces by name
     */
    private static BedrockGeometry parse(final String identifier, final JsonObject description, final JsonObject geometry, final @Nullable JsonObject parent) {
        float textureWidth = number(description.get("texture_width"), 64F);
        float textureHeight = number(description.get("texture_height"), 64F);
        final Map<String, Bone> bones = new LinkedHashMap<>();
        if (parent != null) {
            textureWidth = number(geometry.get("texture_width"), number(parent.get("texture_width"), 64F));
            textureHeight = number(geometry.get("texture_height"), number(parent.get("texture_height"), 64F));
            addBones(bones, parent);
        }
        addBones(bones, geometry);
        return new BedrockGeometry(identifier, textureWidth, textureHeight, List.copyOf(bones.values()));
    }

    private static void addBones(final Map<String, Bone> bones, final JsonObject geometry) {
        if (!(geometry.get("bones") instanceof final JsonArray list)) {
            return;
        }
        for (final JsonElement element : list) {
            if (!(element instanceof final JsonObject bone) || !(bone.get("name") instanceof final JsonElement name)) {
                continue;
            }
            final boolean mirror = bone.get("mirror") instanceof final JsonElement value && value.isJsonPrimitive() && value.getAsBoolean();
            final float inflate = number(bone.get("inflate"), 0F);
            final List<Cube> cubes = new ArrayList<>();
            if (bone.get("cubes") instanceof final JsonArray cubeList) {
                for (final JsonElement cube : cubeList) {
                    if (cube instanceof final JsonObject object) {
                        cubes.add(cube(object, mirror, inflate));
                    }
                }
            }
            final String parent = bone.get("parent") instanceof final JsonElement parentName && parentName.isJsonPrimitive() ? parentName.getAsString() : null;
            // The older format calls a bone's rest rotation its bind pose rotation
            final float[] rotation = vector(bone.has("rotation") ? bone.get("rotation") : bone.get("bind_pose_rotation"), new float[3]);
            bones.put(name.getAsString(), new Bone(name.getAsString(), parent, vector(bone.get("pivot"), new float[3]), rotation, cubes));
        }
    }

    private static Cube cube(final JsonObject cube, final boolean boneMirror, final float boneInflate) {
        final boolean mirror = cube.get("mirror") instanceof final JsonElement value && value.isJsonPrimitive() ? value.getAsBoolean() : boneMirror;
        float[] uv = null;
        final Map<String, float[]> faceUvs = new HashMap<>();
        if (cube.get("uv") instanceof final JsonArray box) {
            uv = vector(box, new float[2]);
        } else if (cube.get("uv") instanceof final JsonObject faces) {
            for (final Map.Entry<String, JsonElement> face : faces.entrySet()) {
                if (face.getValue() instanceof final JsonObject area && area.get("uv") instanceof final JsonArray corner) {
                    final float[] start = vector(corner, new float[2]);
                    final float[] size = vector(area.get("uv_size"), new float[2]);
                    faceUvs.put(face.getKey(), new float[]{start[0], start[1], size[0], size[1]});
                }
            }
        } else {
            uv = new float[2];
        }
        return new Cube(vector(cube.get("origin"), new float[3]), vector(cube.get("size"), new float[3]), number(cube.get("inflate"), boneInflate), mirror,
            cube.has("pivot") ? vector(cube.get("pivot"), new float[3]) : null, cube.has("rotation") ? vector(cube.get("rotation"), new float[3]) : null, uv, faceUvs);
    }

    private static float[] vector(final @Nullable JsonElement element, final float[] fallback) {
        if (!(element instanceof final JsonArray values)) {
            return fallback;
        }
        final float[] vector = new float[fallback.length];
        for (int i = 0; i < vector.length && i < values.size(); i++) {
            vector[i] = number(values.get(i), 0F);
        }
        return vector;
    }

    private static float number(final @Nullable JsonElement element, final float fallback) {
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsFloat();
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

}
