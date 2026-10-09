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

import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.jetbrains.annotations.Nullable;

/**
 * The parts of a server's resource packs ViaBedrock doesn't keep: client entity descriptions with their animations,
 * animations and animation controllers, with packs higher in the stack replacing lower ones.
 */
public final class BedrockPackIndex {

    private static final Map<ResourcePackStorage, BedrockPackIndex> INDEXES = Collections.synchronizedMap(new WeakHashMap<>());

    private final Map<String, JsonObject> entityDescriptions = new HashMap<>();
    private final Map<String, JsonObject> animations = new HashMap<>();
    private final Map<String, JsonObject> animationControllers = new HashMap<>();

    private BedrockPackIndex(final ResourcePackStorage storage) {
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
