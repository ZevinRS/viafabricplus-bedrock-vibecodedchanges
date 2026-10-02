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

package com.viaversion.viafabricplus.bedrock.screen.form;

import com.mojang.blaze3d.platform.NativeImage;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.lenni0451.mcstructs_bedrock.forms.elements.FormImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.api.resourcepack.content.Content;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.jetbrains.annotations.Nullable;

/** Loads the images of Bedrock form buttons, from the server's resource packs or from the web. */
public final class BedrockFormImages {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final int MAX_DOWNLOAD_SIZE = 4 * 1024 * 1024;
    private static final Map<String, Entry> IMAGES = new ConcurrentHashMap<>();

    private BedrockFormImages() {
    }

    /**
     * @return the image, or null while it's loading or if it couldn't be loaded
     */
    public static @Nullable Image get(final FormImage image) {
        final String key = image.getType().getName() + ":" + image.getValue();
        return IMAGES.computeIfAbsent(key, _ -> load(key, image)).image;
    }

    private static Entry load(final String key, final FormImage image) {
        final Entry entry = new Entry();
        final UserConnection user = ViaFabricPlus.api().userConnection();
        final ResourcePackStorage storage = user != null ? user.get(ResourcePackStorage.class) : null;
        CompletableFuture.supplyAsync(() -> switch (image.getType()) {
            case PATH -> readFromPacks(storage, image.getValue());
            case URL -> download(image.getValue());
        }).thenAcceptAsync(png -> {
            if (png == null) {
                return;
            }
            try {
                final NativeImage nativeImage = NativeImage.read(png);
                final Identifier id = Identifier.fromNamespaceAndPath("viafabricplus-bedrock", "form_image/" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Bedrock form image " + key, nativeImage));
                entry.image = new Image(id, nativeImage.getWidth(), nativeImage.getHeight());
            } catch (final Exception e) {
                ViaFabricPlusBedrock.impl().logger().warn("Failed to load Bedrock form image {}", key, e);
            }
        }, Minecraft.getInstance()).exceptionally(e -> {
            ViaFabricPlusBedrock.impl().logger().warn("Failed to load Bedrock form image {}", key, e);
            return null;
        });
        return entry;
    }

    private static byte @Nullable [] readFromPacks(final @Nullable ResourcePackStorage storage, final String value) {
        if (storage == null) {
            return null;
        }
        String path = value.replace('\\', '/');
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        for (final ResourcePack pack : storage.getPackStackTopToBottom()) {
            final Content content = pack.content();
            final String fullPath = content.getFullPath(path, "png", "jpg");
            final Content.LazyImage image = fullPath != null ? content.getImage(fullPath) : null;
            if (image != null) {
                return image.getPngBytes();
            }
        }
        return null; // Textures of the vanilla Bedrock pack aren't available
    }

    private static byte @Nullable [] download(final String url) {
        final URI uri = URI.create(url);
        final String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return null;
        }
        try {
            final HttpResponse<byte[]> response = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2 || response.body().length > MAX_DOWNLOAD_SIZE) {
                return null;
            }
            final BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body())); // Usually PNG or JPEG
            if (image == null) {
                return null;
            }
            final ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            return png.toByteArray();
        } catch (final Exception e) {
            throw new IllegalStateException("Could not download " + url, e);
        }
    }

    public record Image(Identifier texture, int width, int height) {
    }

    private static final class Entry {
        private volatile @Nullable Image image;
    }

}
