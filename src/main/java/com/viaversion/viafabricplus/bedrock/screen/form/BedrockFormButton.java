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

import java.util.ArrayList;
import java.util.List;
import net.lenni0451.mcstructs_bedrock.forms.elements.FormImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * A form button that wraps its text over multiple lines and grows to fit it, like Bedrock form buttons do,
 * instead of scrolling a single line. Labels are drawn as plain text without a button background.
 * <p>
 * Tiles copy the grid layouts of servers' custom form layouts: the button image above the first two lines of the
 * text, with the full text as tooltip.
 */
public final class BedrockFormButton extends Button {

    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 10;

    private static final int TILE_MARGIN = 4;
    private static final int TILE_IMAGE_SIZE = 32;
    private static final int TILE_LINES = 2;
    private static final int TILE_HEIGHT = TILE_MARGIN + TILE_IMAGE_SIZE + 3 + TILE_LINES * LINE_HEIGHT + TILE_MARGIN;

    private final List<FormattedCharSequence> lines;
    private final boolean label;
    private final boolean tile;
    private final @Nullable FormImage image;

    private BedrockFormButton(final int width, final int height, final List<FormattedCharSequence> lines, final Component message, final OnPress onPress, final boolean label, final boolean tile, final @Nullable FormImage image) {
        super(0, 0, width, height, message, onPress, DEFAULT_NARRATION);
        this.lines = lines;
        this.label = label;
        this.tile = tile;
        this.image = image;
        this.active = !label;
    }

    @Override
    protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        if (!this.label) {
            this.extractDefaultSprite(graphics);
        }

        final int centerX = this.getX() + this.getWidth() / 2;
        int y = this.getY() + (this.getHeight() - (this.lines.size() * LINE_HEIGHT - 1)) / 2 + 1;
        if (this.tile) {
            final BedrockFormImages.Image image = this.image != null ? BedrockFormImages.get(this.image) : null;
            if (image != null) {
                final float scale = Math.min((float) TILE_IMAGE_SIZE / image.width(), (float) TILE_IMAGE_SIZE / image.height());
                final int width = Math.max(1, Math.round(image.width() * scale));
                final int height = Math.max(1, Math.round(image.height() * scale));
                final int imageX = centerX - width / 2;
                final int imageY = this.getY() + TILE_MARGIN + (TILE_IMAGE_SIZE - height) / 2;
                graphics.blit(image.texture(), imageX, imageY, imageX + width, imageY + height, 0, 1, 0, 1);
                y = this.getY() + TILE_MARGIN + TILE_IMAGE_SIZE + 3;
            }
        }

        final ActiveTextCollector output = graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE);
        for (final FormattedCharSequence line : this.lines) {
            output.accept(TextAlignment.CENTER, centerX, y, line);
            y += LINE_HEIGHT;
        }
    }

    private static List<FormattedCharSequence> split(final Font font, final Component message, final int width) {
        final List<FormattedCharSequence> lines = new ArrayList<>(font.split(message, width - 2 * TEXT_MARGIN - 4));
        while (!lines.isEmpty() && font.width(lines.getFirst()) == 0) {
            lines.removeFirst();
        }
        while (!lines.isEmpty() && font.width(lines.getLast()) == 0) {
            lines.removeLast();
        }
        if (lines.isEmpty()) {
            lines.add(FormattedCharSequence.EMPTY);
        }
        return lines;
    }

    public static final class Builder extends Button.Builder {

        private final Component message;
        private final OnPress onPress;
        private final int width;
        private final @Nullable Component tooltip;
        private boolean tile;
        private @Nullable FormImage image;

        public Builder(final Component message, final OnPress onPress, final int width, final @Nullable Component tooltip) {
            super(message, onPress);
            this.message = message;
            this.onPress = onPress;
            this.width = width;
            this.tooltip = tooltip;
        }

        public Builder tile(final @Nullable FormImage image) {
            this.tile = true;
            this.image = image;
            return this;
        }

        @Override
        public Button build() {
            final Font font = Minecraft.getInstance().font;
            final boolean label = this.tooltip != null && BedrockForms.isFakeButton(this.tooltip);
            final List<FormattedCharSequence> lines = split(font, this.message, this.width);

            final Button button;
            if (this.tile) {
                button = new BedrockFormButton(this.width, TILE_HEIGHT, lines.subList(0, Math.min(TILE_LINES, lines.size())), this.message, this.onPress, false, true, this.image);
                button.setTooltip(Tooltip.create(this.message));
            } else {
                button = new BedrockFormButton(this.width, Math.max(DEFAULT_HEIGHT, lines.size() * LINE_HEIGHT + PADDING), lines, this.message, this.onPress, label, false, null);
                if (this.tooltip != null && !label) {
                    button.setTooltip(Tooltip.create(this.tooltip));
                }
            }
            return button;
        }

    }

}
