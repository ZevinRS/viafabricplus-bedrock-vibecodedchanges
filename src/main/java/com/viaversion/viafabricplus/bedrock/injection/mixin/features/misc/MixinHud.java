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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.misc;

import com.viaversion.viafabricplus.ViaFabricPlus;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Hive's UI pack draws its sidebar as a row of entries in the top right corner, without title and scores, when the
 * sidebar's title is the address of its UI support page. Java drew that address as the title of a usual sidebar.
 */
@Mixin(Hud.class)
public abstract class MixinHud {

    @Unique
    private static final String HIVE_UI_SIDEBAR = "support.playhive.com/ui";

    @Shadow
    @Final
    private static Comparator<PlayerScoreEntry> SCORE_DISPLAY_ORDER;

    @Shadow
    public abstract Font getFont();

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void drawHiveSidebar(final GuiGraphicsExtractor graphics, final Objective objective, final CallbackInfo ci) {
        if (!ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) || !objective.getDisplayName().getString().equals(HIVE_UI_SIDEBAR)) {
            return;
        }
        ci.cancel();
        final Scoreboard scoreboard = objective.getScoreboard();
        final List<Component> entries = scoreboard.listPlayerScores(objective).stream()
            .filter(score -> !score.isHidden())
            .sorted(SCORE_DISPLAY_ORDER)
            .limit(15L)
            .map(score -> (Component) PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(score.owner()), score.ownerName()))
            .toList();
        // Each entry is its text on a dark background, side by side from the right edge, 2 pixels in and 5 down
        int x = graphics.guiWidth() - 2;
        for (final Component entry : entries) {
            x -= this.getFont().width(entry) + 3;
        }
        final int y = 5;
        for (final Component entry : entries) {
            final int width = this.getFont().width(entry);
            graphics.fill(x, y, x + width + 4, y + 9 + 6, 0x8C000000);
            // Centered on the background: Java's letters are 7 pixels tall in 9 pixel lines
            graphics.text(this.getFont(), entry, x + 1, y + 4, -1, false);
            x += width + 3;
        }
    }

}
