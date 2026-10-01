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

package com.viaversion.viafabricplus.bedrock.protocoltranslator.resourcepack;

import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.awt.image.BufferedImage;
import net.raphimc.viabedrock.api.resourcepack.ResourcePack;
import net.raphimc.viabedrock.api.resourcepack.content.Content;
import net.raphimc.viabedrock.api.resourcepack.content.InMemoryContent;
import net.raphimc.viabedrock.protocol.storage.ResourcePackStorage;
import org.jetbrains.annotations.Nullable;

/**
 * Converts Bedrock glyph sheets (font/glyph_XX.png) into Java bitmap font providers.
 * <p>
 * Bedrock draws glyph pixels at the same size as text pixels regardless of the sheet resolution, so servers draw
 * text sized art into large cells. Bedrock also starts each glyph at its first non-transparent column, while Java
 * measures from the cell's left edge. To match that, every glyph is moved to the left edge of its cell, and the
 * sheet is cropped to the area its glyphs use so large sheets stay small enough for Java's glyph atlas.
 */
public final class BedrockGlyphSheets {

    private static final int GLYPHS_PER_ROW = 16;

    private BedrockGlyphSheets() {
    }

    public static Content convert(final ResourcePackStorage resourcePackStorage) {
        final Content javaContent = new InMemoryContent();
        final JsonArray providers = new JsonArray();

        for (int page = 0; page <= 0xFF; page++) {
            final String sheetPath = "font/glyph_" + String.format("%02X", page) + ".png";
            for (final ResourcePack pack : resourcePackStorage.getPackStackTopToBottom()) {
                final String bedrockPath = findSheet(pack.content(), sheetPath);
                if (bedrockPath == null) {
                    continue;
                }

                final JsonObject provider = convertSheet(javaContent, pack.content().getImage(bedrockPath).getImage(), page);
                if (provider != null) {
                    providers.add(provider);
                }
                break;
            }
        }

        if (!providers.isEmpty()) {
            final JsonObject defaultJson = new JsonObject();
            defaultJson.add("providers", providers);
            javaContent.putJson("assets/minecraft/font/default.json", defaultJson);
        }
        return javaContent;
    }

    private static @Nullable String findSheet(final Content content, final String sheetPath) {
        if (content.contains(sheetPath)) {
            return sheetPath;
        }
        for (final String path : content.getFilesShallow("font/", ".png")) { // Bedrock file names are case-insensitive
            if (path.equalsIgnoreCase(sheetPath)) {
                return path;
            }
        }
        return null;
    }

    private static @Nullable JsonObject convertSheet(final Content javaContent, final BufferedImage sheet, final int page) {
        final int cellWidth = sheet.getWidth() / GLYPHS_PER_ROW;
        final int cellHeight = sheet.getHeight() / GLYPHS_PER_ROW;
        if (cellWidth == 0 || cellHeight == 0) {
            return null;
        }

        // Find the used columns of every glyph and the rows used by any glyph
        final int[] left = new int[GLYPHS_PER_ROW * GLYPHS_PER_ROW];
        int width = 0;
        int top = cellHeight;
        int bottom = 0;
        for (int glyph = 0; glyph < left.length; glyph++) {
            final int cellX = glyph % GLYPHS_PER_ROW * cellWidth;
            final int cellY = glyph / GLYPHS_PER_ROW * cellHeight;
            int minX = cellWidth;
            int maxX = -1;
            for (int y = 0; y < cellHeight; y++) {
                for (int x = 0; x < cellWidth; x++) {
                    if ((sheet.getRGB(cellX + x, cellY + y) >>> 24) != 0) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        top = Math.min(top, y);
                        bottom = Math.max(bottom, y + 1);
                    }
                }
            }
            if (maxX != -1) {
                left[glyph] = minX;
                width = Math.max(width, maxX - minX + 1);
            }
        }
        if (width == 0) {
            return null;
        }

        // Baseline used by ViaBedrock, a few pixels below the cell center. Java requires it inside the glyph area.
        final int baseline = cellHeight / 2 + 5;
        top = Math.min(top, baseline);
        bottom = Math.max(bottom, baseline);
        final int height = bottom - top;

        final BufferedImage converted = new BufferedImage(width * GLYPHS_PER_ROW, height * GLYPHS_PER_ROW, BufferedImage.TYPE_INT_ARGB);
        for (int glyph = 0; glyph < left.length; glyph++) {
            final int cellX = glyph % GLYPHS_PER_ROW * cellWidth + left[glyph];
            final int cellY = glyph / GLYPHS_PER_ROW * cellHeight + top;
            final int targetX = glyph % GLYPHS_PER_ROW * width;
            final int targetY = glyph / GLYPHS_PER_ROW * height;
            final int copyWidth = Math.min(width, cellWidth - left[glyph]);
            final int copyHeight = Math.min(height, cellHeight - top);
            for (int y = 0; y < copyHeight; y++) {
                for (int x = 0; x < copyWidth; x++) {
                    converted.setRGB(targetX + x, targetY + y, sheet.getRGB(cellX + x, cellY + y));
                }
            }
        }

        final String javaPath = "font/glyph_" + String.format("%02x", page) + ".png";
        javaContent.putPngImage("assets/viabedrock/textures/" + javaPath, converted);

        final JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", "viabedrock:" + javaPath);
        provider.addProperty("ascent", baseline - top);
        provider.addProperty("height", height);
        final JsonArray chars = new JsonArray();
        for (int row = 0; row < GLYPHS_PER_ROW; row++) {
            final StringBuilder rowChars = new StringBuilder();
            for (int column = 0; column < GLYPHS_PER_ROW; column++) {
                rowChars.append((char) (page << 8 | row * GLYPHS_PER_ROW + column));
            }
            chars.add(rowChars.toString());
        }
        provider.add("chars", chars);
        return provider;
    }

}
