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
import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.libs.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.DataValues;
import net.raphimc.viabedrock.protocol.model.SkinData;
import org.jetbrains.annotations.Nullable;

/**
 * The skins of Bedrock players, which ViaBedrock only passes to companion mods, so Java drew everyone with a default
 * skin. The Hive's costume NPCs are players too, wearing the costume as their skin. Skins with their own geometry, 4D
 * skins, get a player model of that geometry, see {@link BedrockSkinRenderer}.
 * <p>
 * Skins arrive on the network thread, their textures and models are made on the render thread when first drawn.
 */
public final class BedrockSkins {

    // The bones of Bedrock's player geometry, which only reshape the player when they have other cubes
    private static final Set<String> STANDARD_BONES = Set.of("root", "waist", "body", "jacket", "head", "hat", "leftarm", "rightarm", "leftsleeve", "rightsleeve",
        "leftleg", "rightleg", "leftpants", "rightpants", "cape", "leftitem", "rightitem");

    private static final Map<UUID, Skin> SKINS = new ConcurrentHashMap<>();
    private static @Nullable Map<String, JsonObject> vanillaGeometries;
    private static int textureCount;

    private BedrockSkins() {
    }

    public static void put(final UUID uuid, final SkinData data) {
        final Skin previous = SKINS.put(uuid, new Skin(data));
        if (previous != null) {
            Minecraft.getInstance().execute(previous::release);
        }
    }

    public static void clear() {
        final Map<UUID, Skin> skins = Map.copyOf(SKINS);
        SKINS.clear();
        Minecraft.getInstance().execute(() -> skins.values().forEach(Skin::release));
    }

    public static @Nullable Skin get(final UUID uuid) {
        return SKINS.get(uuid);
    }

    public static final class Skin {

        private final SkinData data;
        private boolean loaded;
        private @Nullable PlayerSkin javaSkin;
        private @Nullable BedrockGeometry geometry;
        private @Nullable Identifier skinTexture;
        private @Nullable Identifier capeTexture;

        Skin(final SkinData data) {
            this.data = data;
        }

        /**
         * @return the skin as Java draws players, with the cape; render thread only
         */
        public @Nullable PlayerSkin javaSkin() {
            this.load();
            return this.javaSkin;
        }

        /**
         * @return the skin's own geometry if it reshapes the player, a 4D skin; render thread only
         */
        public @Nullable BedrockGeometry geometry() {
            this.load();
            return this.geometry;
        }

        public boolean slim() {
            return this.data.armSize().equalsIgnoreCase("slim") || this.geometryName().toLowerCase(Locale.ROOT).contains("slim");
        }

        private void load() {
            if (this.loaded) {
                return;
            }
            this.loaded = true;
            try {
                this.skinTexture = texture(this.data.skinData(), "skin");
                if (this.skinTexture == null) {
                    return;
                }
                this.capeTexture = this.data.capeData() != null && this.data.capeData().getWidth() > 1 ? texture(this.data.capeData(), "cape") : null;
                final ClientAsset.Texture body = new ClientAsset.DownloadedTexture(this.skinTexture, "");
                final ClientAsset.Texture cape = this.capeTexture != null ? new ClientAsset.DownloadedTexture(this.capeTexture, "") : null;
                this.javaSkin = PlayerSkin.insecure(body, cape, cape, this.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE);
                final BedrockGeometry geometry = this.parseGeometry();
                if (geometry != null && reshapes(geometry)) {
                    this.geometry = geometry;
                }
            } catch (final RuntimeException e) {
                ViaFabricPlusBedrock.impl().logger().error("Failed to load a Bedrock skin", e);
            }
        }

        private void release() {
            if (this.skinTexture != null) {
                Minecraft.getInstance().getTextureManager().release(this.skinTexture);
            }
            if (this.capeTexture != null) {
                Minecraft.getInstance().getTextureManager().release(this.capeTexture);
            }
        }

        /**
         * @return the name of the geometry the skin's resource patch picks
         */
        private String geometryName() {
            try {
                final JsonObject patch = JsonParser.parseString(this.data.skinResourcePatch()).getAsJsonObject();
                if (patch.get("geometry") instanceof final JsonObject geometry && geometry.get("default") instanceof final JsonElement name && name.isJsonPrimitive()) {
                    return name.getAsString();
                }
            } catch (final RuntimeException ignored) {
            }
            return "geometry.humanoid.custom";
        }

        private @Nullable BedrockGeometry parseGeometry() {
            if (this.data.geometryData() == null || this.data.geometryData().isBlank()) {
                return null;
            }
            final JsonObject file;
            try {
                file = JsonParser.parseString(this.data.geometryData()).getAsJsonObject();
            } catch (final RuntimeException e) {
                return null;
            }
            final Map<String, BedrockGeometry> geometries = BedrockGeometry.parseFile(file, BedrockSkins::vanillaGeometry);
            final BedrockGeometry geometry = geometries.get(this.geometryName());
            return geometry != null || geometries.size() != 1 ? geometry : geometries.values().iterator().next();
        }

    }

    /**
     * @return whether a geometry has cubes Bedrock's player geometry doesn't, or more than one in a bone of it
     */
    private static boolean reshapes(final BedrockGeometry geometry) {
        for (final BedrockGeometry.Bone bone : geometry.bones()) {
            final boolean standard = STANDARD_BONES.contains(bone.name().toLowerCase(Locale.ROOT));
            if (!bone.cubes().isEmpty() && (!standard || bone.cubes().size() > 1)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Identifier texture(final @Nullable BufferedImage image, final String kind) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            return null;
        }
        final NativeImage nativeImage = new NativeImage(image.getWidth(), image.getHeight(), false);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                nativeImage.setPixel(x, y, image.getRGB(x, y));
            }
        }
        final Identifier identifier = Identifier.fromNamespaceAndPath("viafabricplus-bedrock", "bedrock_skin/" + kind + "_" + textureCount++);
        Minecraft.getInstance().getTextureManager().register(identifier, new DynamicTexture(() -> "Bedrock " + kind, nativeImage));
        return identifier;
    }

    /**
     * Bedrock's own player geometries, which older skin geometries extend by name.
     */
    private static synchronized @Nullable JsonObject vanillaGeometry(final String name) {
        if (vanillaGeometries == null) {
            vanillaGeometries = new HashMap<>();
            try {
                final JsonObject file = BedrockProtocol.MAPPINGS.getBedrockSkinPacks().get(DataValues.VANILLA_SKIN_PACK_KEY).content().getSortedJson("geometry.json");
                if (file.get("minecraft:geometry") instanceof final JsonArray list) {
                    for (final JsonElement element : list) {
                        if (element instanceof final JsonObject geometry && geometry.get("description") instanceof final JsonObject description) {
                            vanillaGeometries.put(description.get("identifier").getAsString(), geometry);
                        }
                    }
                }
            } catch (final RuntimeException ignored) {
            }
        }
        // The oldest skins name the 64 by 32 player geometry
        return vanillaGeometries.get(name.equals("geometry.humanoid") ? "geometry.humanoid.custom" : name);
    }

}
