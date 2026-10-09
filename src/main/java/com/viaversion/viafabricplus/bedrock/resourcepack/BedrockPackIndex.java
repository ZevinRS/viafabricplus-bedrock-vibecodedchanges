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
import com.viaversion.viaversion.util.Key;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.parser.bedrock.geometry.BedrockGeometryParser;
import org.jetbrains.annotations.Nullable;

/**
 * The parts of a server's resource packs ViaBedrock doesn't keep: client entity descriptions with their animations,
 * animations and animation controllers, the blocks of blocks.json, the block textures of terrain_texture.json and the
 * geometries outside models/entity/, with packs higher in the stack replacing lower ones.
 */
public final class BedrockPackIndex {

    private static final Map<ResourcePackStorage, BedrockPackIndex> INDEXES = Collections.synchronizedMap(new WeakHashMap<>());

    private final Map<String, JsonObject> entityDescriptions = new HashMap<>();
    private final Map<String, JsonObject> animations = new HashMap<>();
    private final Map<String, JsonObject> animationControllers = new HashMap<>();
    private final Map<String, JsonObject> blocks = new HashMap<>();
    private final Map<String, String> terrainTextures = new HashMap<>();
    private final ResourcePackStorage storage;
    private Map<String, BedrockGeometryModel> geometries;

    private BedrockPackIndex(final ResourcePackStorage storage) {
        this.storage = storage;
        for (final ResourcePack pack : storage.getPackStackBottomToTop()) {
            for (final String path : pack.content().getFilesDeep("entity/", ".json")) {
                final JsonObject description = object(object(read(pack, path), "minecraft:client_entity"), "description");
                if (description != null && description.has("identifier")) {
                    this.entityDescriptions.put(description.get("identifier").getAsString(), description);
                }
            }
            for (final String path : pack.content().getFilesDeep("animations/", ".json")) {
                putAll(this.animations, object(read(pack, path), "animations"));
            }
            for (final String path : pack.content().getFilesDeep("animation_controllers/", ".json")) {
                putAll(this.animationControllers, object(read(pack, path), "animation_controllers"));
            }
            final JsonObject blocks = read(pack, "blocks.json");
            if (blocks != null) {
                for (final Map.Entry<String, JsonElement> block : blocks.entrySet()) {
                    if (block.getValue().isJsonObject()) {
                        this.blocks.put(Key.namespaced(block.getKey()), block.getValue().getAsJsonObject());
                    }
                }
            }
            final JsonObject textureData = object(read(pack, "textures/terrain_texture.json"), "texture_data");
            if (textureData != null) {
                for (final Map.Entry<String, JsonElement> texture : textureData.entrySet()) {
                    final String path = texturePath(texture.getValue() instanceof final JsonObject entry ? entry.get("textures") : null);
                    if (path != null) {
                        this.terrainTextures.put(texture.getKey(), path);
                    }
                }
            }
        }
    }

    public static BedrockPackIndex of(final ResourcePackStorage storage) {
        return INDEXES.computeIfAbsent(storage, BedrockPackIndex::new);
    }

    public Map<String, JsonObject> entityDescriptions() {
        return this.entityDescriptions;
    }

    public @Nullable JsonObject animation(final String identifier) {
        return this.animations.get(identifier);
    }

    public @Nullable JsonObject animationController(final String identifier) {
        return this.animationControllers.get(identifier);
    }

    /**
     * @return the block's entry in blocks.json
     */
    public @Nullable JsonObject block(final String identifier) {
        return this.blocks.get(identifier);
    }

    /**
     * Full blocks without components for their look, like The Hive's bricks, have their textures in blocks.json: one
     * for all faces or one per face.
     *
     * @return the texture short names by face, * for all faces
     */
    public Map<String, String> blockTextures(final String identifier) {
        final Map<String, String> textures = new HashMap<>();
        final JsonObject block = this.blocks.get(identifier);
        if (block != null && block.get("textures") instanceof final JsonElement blockTextures) {
            if (blockTextures.isJsonPrimitive()) {
                textures.put("*", blockTextures.getAsString());
            } else if (blockTextures instanceof final JsonObject faces) {
                for (final Map.Entry<String, JsonElement> face : faces.entrySet()) {
                    if (face.getValue().isJsonPrimitive()) {
                        textures.put(face.getKey(), face.getValue().getAsString());
                    }
                }
            }
        }
        return textures;
    }

    /**
     * ViaBedrock only keeps the geometries of models/entity/, while blocks have theirs in models/blocks/ and the like.
     *
     * @return the geometry of any model file, parsed on the first call
     */
    public synchronized @Nullable BedrockGeometryModel geometry(final String identifier) {
        if (this.geometries == null) {
            this.geometries = new HashMap<>();
            for (final ResourcePack pack : this.storage.getPackStackBottomToTop()) {
                for (final String path : pack.content().getFilesDeep("models/", ".json")) {
                    try {
                        for (final BedrockGeometryModel geometry : BedrockGeometryParser.parse(pack.content().getString(path))) {
                            this.geometries.put(geometry.getIdentifier(), geometry);
                        }
                    } catch (final Throwable ignored) { // ViaBedrock already warns about the ones it can't parse
                    }
                }
            }
        }
        return this.geometries.get(identifier);
    }

    /**
     * @return the path of a block texture's short name in terrain_texture.json, without file extension
     */
    public @Nullable String terrainTexture(final String shortName) {
        return this.terrainTextures.get(shortName);
    }

    /**
     * @return the first texture path of a terrain texture entry: a path, a list of variants or an object with a path
     */
    private static @Nullable String texturePath(final @Nullable JsonElement textures) {
        if (textures == null) {
            return null;
        } else if (textures.isJsonPrimitive()) {
            return textures.getAsString();
        } else if (textures instanceof final JsonArray variants && !variants.isEmpty()) {
            return texturePath(variants.get(0));
        } else if (textures instanceof final JsonObject object && object.has("path")) {
            return object.get("path").getAsString();
        }
        return null;
    }

    private static void putAll(final Map<String, JsonObject> target, final @Nullable JsonObject source) {
        if (source != null) {
            for (final Map.Entry<String, JsonElement> entry : source.entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    target.put(entry.getKey(), entry.getValue().getAsJsonObject());
                }
            }
        }
    }

    private static @Nullable JsonObject read(final ResourcePack pack, final String path) {
        try {
            return pack.content().getJson(path);
        } catch (final Exception e) { // Packs often have files that aren't valid JSON
            return null;
        }
    }

    static @Nullable JsonObject object(final @Nullable JsonElement element, final String key) {
        return element != null && element.isJsonObject() && element.getAsJsonObject().get(key) instanceof final JsonObject object ? object : null;
    }

}
