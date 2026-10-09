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

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.api.resourcepack.content.Content;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Decoded textures of a server's packs, each decoded once: models share their textures, and the packs of servers like
 * The Hive have hundreds of models.
 */
public final class BedrockPackImages {

    private static final Map<ResourcePackStorage, Map<String, Optional<BufferedImage>>> IMAGES = Collections.synchronizedMap(new WeakHashMap<>());

    static {
        ImageIO.setUseCache(false); // Decodes in memory instead of through temporary files
    }

    private BedrockPackImages() {
    }

    /**
     * @param path the texture path without file extension, from the top of the pack stack
     */
    public static @Nullable BufferedImage get(final ResourcePackStorage storage, final String path) {
        return IMAGES.computeIfAbsent(storage, s -> new ConcurrentHashMap<>()).computeIfAbsent(path, p -> {
            for (final ResourcePack pack : storage.getPackStackTopToBottom()) {
                final Content.LazyImage image = pack.content().getShortnameImage(p);
                if (image != null) {
                    return Optional.ofNullable(image.getImage());
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

}
