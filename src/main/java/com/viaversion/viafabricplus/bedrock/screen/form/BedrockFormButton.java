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
 */
public final class BedrockFormButton extends Button {

    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 10;
    private static final int MAX_LINES = 3;

    private final List<FormattedCharSequence> lines;
    private final boolean label;

    private BedrockFormButton(final int width, final List<FormattedCharSequence> lines, final Component message, final OnPress onPress, final boolean label) {
        super(0, 0, width, Math.max(DEFAULT_HEIGHT, lines.size() * LINE_HEIGHT + PADDING), message, onPress, DEFAULT_NARRATION);
        this.lines = lines;
        this.label = label;
        this.active = !label;
    }

    @Override
    protected void extractContents(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        if (!this.label) {
            this.extractDefaultSprite(graphics);
        }

        final ActiveTextCollector output = graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE);
        final int centerX = this.getX() + this.getWidth() / 2;
        int y = this.getY() + (this.getHeight() - (this.lines.size() * LINE_HEIGHT - 1)) / 2 + 1;
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

    /**
     * Long button texts are usually shown partially by servers' custom Bedrock UIs, with the rest in a tooltip.
     * Keep the first paragraph on the button and move the full text into the tooltip.
     *
     * @return the first paragraph, or null if the text is short enough to show completely
     */
    private static @Nullable List<FormattedCharSequence> firstParagraph(final Font font, final List<FormattedCharSequence> lines) {
        if (lines.size() <= MAX_LINES) {
            return null;
        }
        for (int i = 1; i < lines.size(); i++) {
            if (font.width(lines.get(i)) == 0) {
                return lines.subList(0, i);
            }
        }
        return null;
    }

    public static final class Builder extends Button.Builder {

        private final Component message;
        private final OnPress onPress;
        private final int width;
        private final @Nullable Component tooltip;

        public Builder(final Component message, final OnPress onPress, final int width, final @Nullable Component tooltip) {
            super(message, onPress);
            this.message = message;
            this.onPress = onPress;
            this.width = width;
            this.tooltip = tooltip;
        }

        @Override
        public Button build() {
            final Font font = Minecraft.getInstance().font;
            final boolean label = this.tooltip != null && BedrockForms.isFakeButton(this.tooltip);
            final List<FormattedCharSequence> lines = split(font, this.message, this.width);
            final List<FormattedCharSequence> paragraph = label ? null : firstParagraph(font, lines);

            final Button button = new BedrockFormButton(this.width, paragraph != null ? paragraph : lines, this.message, this.onPress, label);
            if (paragraph != null) {
                button.setTooltip(Tooltip.create(this.message));
            } else if (this.tooltip != null && !label) {
                button.setTooltip(Tooltip.create(this.tooltip));
            }
            return button;
        }

    }

}
