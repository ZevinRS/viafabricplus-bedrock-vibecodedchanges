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

import com.mojang.blaze3d.platform.NativeImage;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockPackImages;
import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockPackIndex;
import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.model.entity.CustomEntity;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.jetbrains.annotations.Nullable;

/**
 * The custom entities drawn with their Bedrock models and animations, by the Java id of the entity ViaBedrock spawns
 * for them, see {@link BedrockEntityRenderer}. Filled on the network thread, read on the render thread.
 */
public final class BedrockEntityModels {

    private static final Map<Integer, Instance> INSTANCES = new ConcurrentHashMap<>();
    private static final Map<ResourcePackStorage, Map<String, Optional<BedrockModel>>> MODELS = Collections.synchronizedMap(new WeakHashMap<>());
    // Render thread only
    private static final Map<String, Identifier> TEXTURES = new HashMap<>();
    private static @Nullable ResourcePackStorage texturePacks;
    private static int textureCount;

    private BedrockEntityModels() {
    }

    /**
     * @param texture    the texture path in the packs, without file extension
     * @param fullBright whether its render controller ignores lighting, like The Hive's logo
     */
    public record Part(BedrockModel model, String texture, BedrockMaterial material, boolean fullBright) {
    }

    public static final class Instance {

        private final ResourcePackStorage packs;
        private final CustomEntity entity;
        private final String identifier;
        private final List<Part> parts;
        private final BedrockAnimator animator;
        double distanceMoved;
        double lastYaw = Double.NaN;
        @Nullable Vec3 lastPosition;

        Instance(final ResourcePackStorage packs, final CustomEntity entity, final String identifier, final List<Part> parts, final BedrockAnimator animator) {
            this.packs = packs;
            this.entity = entity;
            this.identifier = identifier;
            this.parts = parts;
            this.animator = animator;
        }

        public ResourcePackStorage packs() {
            return this.packs;
        }

        public CustomEntity entity() {
            return this.entity;
        }

        /**
         * @return the custom entity's Bedrock identifier
         */
        public String identifier() {
            return this.identifier;
        }

        public List<Part> parts() {
            return this.parts;
        }

        public BedrockAnimator animator() {
            return this.animator;
        }

    }

    /**
     * Makes the models of a custom entity, by the geometries and textures its render controllers chose.
     *
     * @return whether every model could be made, otherwise ViaBedrock draws the entity
     */
    public static boolean spawn(final ResourcePackStorage packs, final CustomEntity entity, final String identifier, final List<CustomEntity.EvaluatedModel> evaluatedModels) {
        final BedrockPackIndex index = BedrockPackIndex.of(packs);
        final JsonObject description = index.entityDescriptions().get(identifier);
        if (description == null || evaluatedModels.isEmpty()) {
            return false;
        }
        final JsonObject renderController = renderController(index, description);
        final BedrockMaterial material = material(index, description, renderController);
        final boolean fullBright = renderController != null && renderController.get("ignore_lighting") instanceof final JsonElement ignoreLighting
            && ignoreLighting.isJsonPrimitive() && ignoreLighting.getAsBoolean();
        final List<Part> parts = new ArrayList<>();
        for (final CustomEntity.EvaluatedModel evaluated : evaluatedModels) {
            final BedrockModel model = model(packs, index, evaluated.geometryValue());
            if (model == null) {
                return false;
            }
            parts.add(new Part(model, evaluated.textureValue(), material, fullBright));
        }
        final Instance previous = INSTANCES.get(entity.javaId());
        final BedrockAnimator animator = previous != null && previous.entity == entity ? previous.animator : new BedrockAnimator(index, description);
        INSTANCES.put(entity.javaId(), new Instance(packs, entity, identifier, List.copyOf(parts), animator));
        return true;
    }

    public static void remove(final int javaId) {
        INSTANCES.remove(javaId);
    }

    public static void clear() {
        INSTANCES.clear();
    }

    public static @Nullable Instance get(final int javaId) {
        return INSTANCES.get(javaId);
    }

    private static @Nullable BedrockModel model(final ResourcePackStorage packs, final BedrockPackIndex index, final String geometry) {
        return MODELS.computeIfAbsent(packs, p -> new ConcurrentHashMap<>()).computeIfAbsent(geometry, g -> {
            final BedrockGeometry parsed = index.renderGeometry(g);
            if (parsed == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(BedrockModel.of(parsed));
            } catch (final RuntimeException e) {
                ViaFabricPlusBedrock.impl().logger().error("Failed to make the model of {}", g, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static @Nullable JsonObject renderController(final BedrockPackIndex index, final JsonObject description) {
        if (description.get("render_controllers") instanceof final JsonArray controllers && !controllers.isEmpty()) {
            final JsonElement first = controllers.get(0);
            final String identifier = first.isJsonPrimitive() ? first.getAsString()
                : first instanceof final JsonObject conditional && !conditional.isEmpty() ? conditional.keySet().iterator().next() : null;
            return identifier != null ? index.renderController(identifier) : null;
        }
        return null;
    }

    /**
     * @return the material the render controller gives the whole model, from the materials the entity names
     */
    private static BedrockMaterial material(final BedrockPackIndex index, final JsonObject description, final @Nullable JsonObject renderController) {
        if (!(description.get("materials") instanceof final JsonObject materials) || materials.isEmpty()) {
            return BedrockMaterial.ALPHA_TEST;
        }
        String key = "default";
        if (renderController != null && renderController.get("materials") instanceof final JsonArray controllerMaterials && !controllerMaterials.isEmpty()
            && controllerMaterials.get(0) instanceof final JsonObject byBone && !byBone.isEmpty()
            && byBone.asMap().values().iterator().next() instanceof final JsonElement reference && reference.isJsonPrimitive()) {
            final String value = reference.getAsString();
            key = value.regionMatches(true, 0, "material.", 0, 9) ? value.substring(9) : value;
        }
        final JsonElement name = materials.has(key) ? materials.get(key) : materials.asMap().values().iterator().next();
        return name.isJsonPrimitive() ? BedrockMaterial.resolve(index, name.getAsString()) : BedrockMaterial.ALPHA_TEST;
    }

    /**
     * @return the render type of a part, with its texture loaded from the packs on the first call; render thread only
     */
    public static @Nullable RenderType renderType(final ResourcePackStorage packs, final Part part) {
        if (texturePacks != packs) {
            // A new server's packs: the textures of the last ones aren't used anymore
            TEXTURES.values().forEach(Minecraft.getInstance().getTextureManager()::release);
            TEXTURES.clear();
            texturePacks = packs;
        }
        Identifier texture = TEXTURES.get(part.texture());
        if (texture == null && !TEXTURES.containsKey(part.texture())) {
            texture = loadTexture(packs, part.texture());
            TEXTURES.put(part.texture(), texture);
        }
        if (texture == null) {
            return null;
        }
        return part.material().renderType(texture);
    }

    private static @Nullable Identifier loadTexture(final ResourcePackStorage packs, final String path) {
        final BufferedImage image = BedrockPackImages.get(packs, path);
        if (image == null) {
            return null;
        }
        final NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                nativeImage.setPixel(x, y, image.getRGB(x, y));
            }
        }
        final Identifier identifier = Identifier.fromNamespaceAndPath("viafabricplus-bedrock", "bedrock_entity/" + textureCount++);
        Minecraft.getInstance().getTextureManager().register(identifier, new DynamicTexture(() -> path, nativeImage));
        return identifier;
    }

}
