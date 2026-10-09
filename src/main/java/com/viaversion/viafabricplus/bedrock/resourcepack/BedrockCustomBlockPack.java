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

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.block.BedrockCustomBlockDefinition;
import com.viaversion.viafabricplus.bedrock.block.BedrockCustomBlocks;
import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.api.resourcepack.content.Content;
import net.raphimc.viabedrock.api.resourcepack.content.InMemoryContent;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.cube.converter.converter.enums.RotationType;
import org.cube.converter.model.impl.bedrock.BedrockGeometryModel;
import org.cube.converter.util.element.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * The models of the blocks a server defines, as a resource pack for the blocks of the pool they were given, see
 * {@link BedrockCustomBlocks}. The server only defines its blocks once its resource packs were converted and loaded, so
 * this pack is loaded on top afterwards, like another server resource pack, unless the same models were loaded with the
 * packs already, see {@link BedrockBlockPackCache}.
 */
public final class BedrockCustomBlockPack {

    // Identifies the pack to the client's server pack handling, which doesn't report it to the server
    public static final UUID PACK_ID = UUID.nameUUIDFromBytes("viafabricplus-bedrock:custom_blocks".getBytes());

    private BedrockCustomBlockPack() {
    }

    public static void build(final ResourcePackStorage packs) {
        final BedrockCustomBlockDefinition[] full = BedrockCustomBlocks.fullDefinitions();
        final BedrockCustomBlockDefinition[] shaped = BedrockCustomBlocks.shapedDefinitions();
        CompletableFuture.runAsync(() -> {
            try {
                final Content content = new InMemoryContent();
                final Map<String, String> textures = new HashMap<>();
                final JsonObject lang = new JsonObject();
                for (int i = 0; i < full.length && full[i] != null; i++) {
                    addBlockSafely(content, packs, textures, lang, BedrockCustomBlocks.fullBlock(i), full[i]);
                }
                for (int i = 0; i < shaped.length && shaped[i] != null; i++) {
                    addBlockSafely(content, packs, textures, lang, BedrockCustomBlocks.shapedBlock(i), shaped[i]);
                }
                content.putJson("assets/" + BedrockCustomBlocks.NAMESPACE + "/lang/en_us.json", lang);
                content.putJson("pack.mcmeta", manifest());
                if (BedrockBlockPackCache.isLoaded(packs, content)) {
                    ViaFabricPlusBedrock.impl().logger().info("The models of the server's blocks were loaded with its packs");
                    return;
                }
                final byte[] zip = content.toZip();
                BedrockBlockPackCache.keep(packs, zip);
                final Path file = Files.createTempFile("viafabricplus-bedrock-blocks", ".zip");
                file.toFile().deleteOnExit();
                Files.write(file, zip);
                ViaFabricPlusBedrock.impl().logger().info("Made the models of the server's blocks, {} textures", textures.size());
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().getDownloadedPackSource().popPack(PACK_ID);
                    Minecraft.getInstance().getDownloadedPackSource().pushLocalPack(PACK_ID, file);
                });
            } catch (final Throwable e) {
                ViaFabricPlusBedrock.impl().logger().error("Failed to make the models of the server's blocks", e);
            }
        });
    }

    private static void addBlockSafely(final Content content, final ResourcePackStorage packs, final Map<String, String> textures, final JsonObject lang,
                                       final Block block, final BedrockCustomBlockDefinition definition) {
        try {
            addBlock(content, packs, textures, lang, block, definition);
        } catch (final Throwable e) {
            ViaFabricPlusBedrock.impl().logger().error("Failed to make the model of {}", definition.name(), e);
        }
    }

    private static void addBlock(final Content content, final ResourcePackStorage packs, final Map<String, String> textures, final JsonObject lang,
                                 final Block block, final BedrockCustomBlockDefinition definition) {
        final String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        final String model = BedrockCustomBlocks.NAMESPACE + ":block/" + path;
        final String assets = "assets/" + BedrockCustomBlocks.NAMESPACE + "/";

        final Map<String, String> shortNames = definition.textures().isEmpty() ? BedrockPackIndex.of(packs).blockTextures(definition.name()) : definition.textures();
        final Map<Direction, String> faceTextures = new EnumMap<>(Direction.class);
        for (final Direction direction : Direction.values()) {
            final String texture = texture(content, packs, textures, faceTexture(shortNames, direction));
            faceTextures.put(direction, texture != null ? texture : "minecraft:block/stone");
        }

        JsonObject blockModel = null;
        if (definition.geometry() != null) {
            final BedrockGeometryModel geometry = BedrockPackIndex.of(packs).geometry(definition.geometry());
            if (geometry != null) {
                blockModel = geometry.toJavaItemModel(faceTextures, RotationType.POST_1_21_11).compile();
                blockModel.remove("display");
                blockModel.addProperty("parent", "minecraft:block/block");
                blockModel.getAsJsonObject("textures").addProperty("particle", faceTextures.get(Direction.NORTH));
                removeUntexturedFaces(blockModel);
                if (isDoubleSided(definition.renderMethod())) {
                    BedrockDoubleSidedPlanes.addBackFaces(blockModel);
                }
            }
        }
        if (blockModel == null) {
            blockModel = new JsonObject();
            blockModel.addProperty("parent", "minecraft:block/cube");
            final JsonObject cubeTextures = new JsonObject();
            for (final Map.Entry<Direction, String> face : faceTextures.entrySet()) {
                cubeTextures.addProperty(face.getKey().name().toLowerCase(Locale.ROOT), face.getValue());
            }
            cubeTextures.addProperty("particle", faceTextures.get(Direction.NORTH));
            blockModel.add("textures", cubeTextures);
        }
        content.putJson(assets + "models/block/" + path + ".json", sorted(blockModel));

        final JsonObject variant = new JsonObject();
        variant.addProperty("model", model);
        // Bedrock turns counterclockwise seen from above, Java's blockstates clockwise
        final int yaw = Math.floorMod(-Math.round(definition.rotation()[1] / 90F) * 90, 360);
        if (yaw != 0) {
            variant.addProperty("y", yaw);
        }
        final JsonObject variants = new JsonObject();
        variants.add("", variant);
        final JsonObject blockState = new JsonObject();
        blockState.add("variants", variants);
        content.putJson(assets + "blockstates/" + path + ".json", blockState);

        final JsonObject itemModel = new JsonObject();
        itemModel.addProperty("type", "minecraft:model");
        itemModel.addProperty("model", model);
        final JsonObject item = new JsonObject();
        item.add("model", itemModel);
        content.putJson(assets + "items/" + path + ".json", item);

        final String nameKey = "tile." + definition.name() + ".name";
        final String name = packs.getTexts().get(nameKey);
        lang.addProperty(block.getDescriptionId(), name.equals(nameKey) ? definition.name() : name);
    }

    /**
     * The converter keeps some parts of a model in hash maps, whose order changes between starts of the game, so the
     * keys are sorted to make the same pack every time, see {@link BedrockBlockPackCache}.
     */
    private static JsonObject sorted(final JsonObject object) {
        final JsonObject sorted = new JsonObject();
        object.keySet().stream().sorted().forEach(key -> sorted.add(key, sorted(object.get(key))));
        return sorted;
    }

    private static JsonElement sorted(final JsonElement element) {
        if (element instanceof final JsonObject object) {
            return sorted(object);
        }
        if (element instanceof final JsonArray array) {
            final JsonArray sorted = new JsonArray();
            array.forEach(item -> sorted.add(sorted(item)));
            return sorted;
        }
        return element;
    }

    /**
     * Bedrock's alpha_test and blend materials draw faces from both sides, their *_single_sided forms and opaque don't.
     */
    private static boolean isDoubleSided(final @Nullable String renderMethod) {
        return renderMethod != null && !renderMethod.equals("opaque") && !renderMethod.endsWith("single_sided");
    }

    /**
     * Faces the geometry has no texture area for come out referencing a texture the model doesn't have, which Java
     * draws as the missing texture.
     */
    private static void removeUntexturedFaces(final JsonObject model) {
        final JsonObject textures = model.getAsJsonObject("textures");
        if (!(model.get("elements") instanceof final JsonArray elements)) {
            return;
        }
        final JsonArray textured = new JsonArray();
        for (final JsonElement element : elements) {
            if (element instanceof final JsonObject object && object.get("faces") instanceof final JsonObject faces) {
                faces.entrySet().removeIf(face -> !(face.getValue() instanceof final JsonObject faceObject)
                    || !(faceObject.get("texture") instanceof final JsonElement texture)
                    || !textures.has(texture.getAsString().substring(texture.getAsString().startsWith("#") ? 1 : 0)));
                if (!faces.isEmpty()) { // Java doesn't load parts without faces
                    // The converter names parts at random, which would make every build of the pack differ
                    object.remove("name");
                    textured.add(object);
                }
            }
        }
        model.add("elements", textured);
        model.remove("groups"); // They refer to parts by index
    }

    /**
     * @return the texture short name of a face: its own, then side for the sides, then the one for all faces
     */
    private static @Nullable String faceTexture(final Map<String, String> textures, final Direction direction) {
        final String face = direction.name().toLowerCase(Locale.ROOT);
        if (textures.containsKey(face)) {
            return textures.get(face);
        }
        if (direction != Direction.UP && direction != Direction.DOWN && textures.containsKey("side")) {
            return textures.get("side");
        }
        if (textures.containsKey("*")) {
            return textures.get("*");
        }
        return textures.isEmpty() ? null : textures.values().iterator().next();
    }

    /**
     * Copies the texture of a short name from the packs, once.
     *
     * @return its Java texture name
     */
    private static @Nullable String texture(final Content content, final ResourcePackStorage packs, final Map<String, String> copied, final @Nullable String shortName) {
        if (shortName == null) {
            return null;
        }
        if (copied.containsKey(shortName)) {
            return copied.get(shortName);
        }
        final String bedrockPath = BedrockPackIndex.of(packs).terrainTexture(shortName);
        String javaTexture = null;
        if (bedrockPath != null) {
            for (final ResourcePack pack : packs.getPackStackTopToBottom()) {
                final Content.LazyImage image = pack.content().getShortnameImage(bedrockPath);
                if (image != null) {
                    final String javaPath = "bedrock/" + bedrockPath.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
                    content.putPngImage("assets/" + BedrockCustomBlocks.NAMESPACE + "/textures/block/" + javaPath + ".png", image);
                    javaTexture = BedrockCustomBlocks.NAMESPACE + ":block/" + javaPath;
                    break;
                }
            }
        }
        copied.put(shortName, javaTexture);
        return javaTexture;
    }

    private static JsonObject manifest() {
        final JsonObject pack = new JsonObject();
        pack.addProperty("description", "ViaFabricPlus Bedrock: the server's blocks");
        pack.addProperty("min_format", 88);
        pack.addProperty("max_format", 88);
        final JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

}
