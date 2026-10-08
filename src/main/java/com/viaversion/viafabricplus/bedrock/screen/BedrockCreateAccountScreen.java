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

package com.viaversion.viafabricplus.bedrock.screen;

import com.mojang.blaze3d.Blaze3D;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.account.BedrockAccount;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import java.net.URI;
import java.security.SecureRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import org.jetbrains.annotations.Nullable;

/**
 * Walks through making a new Microsoft account to test with. Microsoft only allows people to create accounts, so the
 * steps open its own pages to fill out in the browser, and the new account is logged in to at the end.
 */
public final class BedrockCreateAccountScreen extends VFPScreen {

    public static final Component TITLE = Component.translatable("screen.viafabricplus.bedrock_create_account");

    private static final String SIGN_UP = "https://signup.live.com/signup?lic=1";
    private static final String PASSWORD_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!#$%&*+-=?@";
    private static final int PASSWORD_LENGTH = 20;
    private static final int STEP_HEIGHT = 56;
    private static final int BUTTON_WIDTH = 146;
    private static final int SECONDARY_COLOR = 0xFFB8B8B8;

    private @Nullable String password;
    // The account in use when the login was started, to notice when the new one was added
    private @Nullable BedrockAuthManager accountBeforeLogin;
    private boolean loggingIn;

    public BedrockCreateAccountScreen() {
        super(TITLE, true);
    }

    @Override
    protected void init() {
        final int left = this.width / 2 - 150;
        this.addRenderableWidget(Button.builder(Component.translatable("bedrock_create_account.viafabricplus.open_sign_up"), _ -> open(SIGN_UP))
            .pos(left, this.stepTop(0) + 26).size(BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("bedrock_create_account.viafabricplus.copy_password"), _ -> this.copyPassword())
            .pos(left + BUTTON_WIDTH + 8, this.stepTop(0) + 26).size(BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("bedrock_create_account.viafabricplus.open_xbox"), _ -> open(BedrockAccount.XBOX_SIGN_IN))
            .pos(left, this.stepTop(1) + 26).size(BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("bedrock_create_account.viafabricplus.log_in"), _ -> this.logIn())
            .pos(left, this.stepTop(2) + 26).size(BUTTON_WIDTH, 20).build());
        super.init();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.loggingIn && ViaFabricPlusBedrock.impl().account().get() != this.accountBeforeLogin) {
            this.onClose(); // Back to the accounts, with the new one in use
        }
    }

    @Override
    public void renderTitle(final GuiGraphicsExtractor graphics) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(2F, 2F);
        graphics.centeredText(this.font, this.title, this.width / 4, 6, ACCENT_COLOR);
        graphics.pose().popMatrix();
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        final int left = this.width / 2 - 150;
        for (int step = 0; step < 3; step++) {
            final int top = this.stepTop(step);
            graphics.text(this.font, Component.translatable("bedrock_create_account.viafabricplus.step" + step), left, top, ACCENT_COLOR);
            graphics.text(this.font, Component.translatable("bedrock_create_account.viafabricplus.step" + step + ".detail"), left, top + 12, SECONDARY_COLOR);
        }
        if (this.password != null) {
            graphics.text(this.font, Component.translatable("bedrock_create_account.viafabricplus.password", this.password),
                left + BUTTON_WIDTH + 8, this.stepTop(0) + 50, SECONDARY_COLOR);
        }
    }

    private int stepTop(final int step) {
        return 40 + step * STEP_HEIGHT;
    }

    /**
     * Opens the page in the browser and copies its link, for pasting into a private window that isn't signed in to
     * another account.
     */
    private static void open(final String url) {
        Blaze3D.openUri(URI.create(url));
        Minecraft.getInstance().keyboardHandler.setClipboard(url);
        VFPScreen.showToast(Component.translatable("bedrock_create_account.viafabricplus.link_copied"));
    }

    private void copyPassword() {
        final SecureRandom random = new SecureRandom();
        final StringBuilder password = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_CHARACTERS.charAt(random.nextInt(PASSWORD_CHARACTERS.length())));
        }
        this.password = password.toString();
        Minecraft.getInstance().keyboardHandler.setClipboard(this.password);
        VFPScreen.showToast(Component.translatable("bedrock_create_account.viafabricplus.password_copied"));
    }

    private void logIn() {
        this.accountBeforeLogin = ViaFabricPlusBedrock.impl().account().get();
        this.loggingIn = true;
        ViaFabricPlusBedrock.impl().account().login();
    }

}
