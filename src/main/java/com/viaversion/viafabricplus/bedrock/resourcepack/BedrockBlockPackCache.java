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

import com.viaversion.viafabricplus.bedrock.feature.Features;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.api.resourcepack.content.Content;
import net.raphimc.viabedrock.api.resourcepack.content.ZipContent;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;

/**
 * The models of a server's blocks can only be made once it defines its blocks, after Java loaded the server's packs,
 * which then loaded them all again: The Hive took over 15 seconds more on every join and transfer. The models made for
 * a server's packs are kept, and loaded with the packs the next time, so only blocks that changed load them again.
 */
public final class BedrockBlockPackCache {

    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("viafabricplus").resolve("bedrock_block_packs");
    // The block models loaded with each server's packs, by the hash of their files
    private static final Map<ResourcePackStorage, String> LOADED = Collections.synchronizedMap(new WeakHashMap<>());

    private BedrockBlockPackCache() {
    }

    /**
     * Adds the block models kept for the packs to the packs ViaBedrock converted for Java.
     */
    public static void addTo(final ResourcePackStorage packs, final Content javaContent) {
        final Path file = file(packs);
        if (!Features.BLOCK_MODEL_CACHE.isEnabled() || !Features.CUSTOM_BLOCKS.isEnabled() || !Files.isRegularFile(file)) {
            return;
        }
        try {
            final Content cached = new ZipContent(Files.readAllBytes(file));
            for (final String path : cached.getFilesDeep("", "")) {
                if (!path.equals("pack.mcmeta")) {
                    javaContent.put(path, cached.get(path));
                }
            }
            LOADED.put(packs, hash(cached));
        } catch (final Exception e) {
            ViaFabricPlusBedrock.impl().logger().warn("Failed to load the kept models of the server's blocks", e);
        }
    }

    /**
     * @return whether the block models were loaded with the packs already
     */
    public static boolean isLoaded(final ResourcePackStorage packs, final Content blockPack) {
        final String loaded = LOADED.get(packs);
        return loaded != null && loaded.equals(hash(blockPack));
    }

    /**
     * Keeps the block models for the next time the packs are loaded.
     */
    public static void keep(final ResourcePackStorage packs, final byte[] zip) {
        if (!Features.BLOCK_MODEL_CACHE.isEnabled()) {
            return;
        }
        try {
            Files.createDirectories(DIRECTORY);
            Files.write(file(packs), zip);
        } catch (final IOException e) {
            ViaFabricPlusBedrock.impl().logger().warn("Failed to keep the models of the server's blocks", e);
        }
    }

    private static Path file(final ResourcePackStorage packs) {
        final StringBuilder stack = new StringBuilder();
        for (final ResourcePack pack : packs.getPackStackBottomToTop()) {
            stack.append(pack.key()).append('\n');
        }
        return DIRECTORY.resolve(sha1(stack.toString().getBytes()) + ".zip");
    }

    private static String hash(final Content content) {
        final List<String> paths = new ArrayList<>(content.getFilesDeep("", ""));
        Collections.sort(paths);
        final MessageDigest digest = digest();
        for (final String path : paths) {
            if (!path.equals("pack.mcmeta")) {
                digest.update(path.getBytes());
                digest.update(content.get(path));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha1(final byte[] data) {
        return HexFormat.of().formatHex(digest().digest(data));
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
