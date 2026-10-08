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

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.account.BedrockAccount;
import com.viaversion.viafabricplus.bedrock.account.PrivateBrowser;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import java.security.SecureRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import org.jetbrains.annotations.Nullable;

/**
 * Walks through making a new Microsoft account to test with. Microsoft only allows people to create accounts, so the
 * steps open its own pages to fill out in a private browser window, and the new account is logged in to at the end.
 */
public final class BedrockCreateAccountScreen extends VFPScreen {

    public static final Component TITLE = Component.translatable("screen.viafabricplus.bedrock_create_account");

    // Outlook's "Create free account", which makes a new @outlook.com address instead of asking for an existing one
    private static final String SIGN_UP = "https://go.microsoft.com/fwlink/p/?linkid=2125440";
    private static final String ADDRESS_CHARACTERS = "abcdefghijkmnopqrstuvwxyz23456789";
    private static final int ADDRESS_LENGTH = 14;
    private static final String PASSWORD_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!#$%&*+-=?@";
    private static final int PASSWORD_LENGTH = 20;
    private static final int STEP_HEIGHT = 64;
    private static final int BUTTON_WIDTH = 96;
    private static final int BUTTON_GAP = 6;
    private static final int SECONDARY_COLOR = 0xFFB8B8B8;

    private final SecureRandom random = new SecureRandom();
    private @Nullable String address;
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
        this.addRenderableWidget(Button.builder(Component.translatable("bedrock_create_account.viafabricplus.copy_address"), _ -> this.copyAddress())
            .pos(left + BUTTON_WIDTH + BUTTON_GAP, this.stepTop(0) + 26).size(BUTTON_WIDTH, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("bedrock_create_account.viafabricplus.copy_password"), _ -> this.copyPassword())
            .pos(left + (BUTTON_WIDTH + BUTTON_GAP) * 2, this.stepTop(0) + 26).size(BUTTON_WIDTH, 20).build());
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
        if (this.address != null) {
            graphics.text(this.font, this.address + "@outlook.com", left, this.stepTop(0) + 50, -1);
        }
        if (this.password != null) {
            graphics.text(this.font, this.password, left + (BUTTON_WIDTH + BUTTON_GAP) * 2, this.stepTop(0) + 50, -1);
        }
    }

    private int stepTop(final int step) {
        return 34 + step * STEP_HEIGHT;
    }

    private static void open(final String url) {
        PrivateBrowser.open(url);
    }

    /**
     * Copies a random name for the new address, which the sign-up page completes with @outlook.com. Names have to
     * start with a letter.
     */
    private void copyAddress() {
        this.address = (char) ('a' + this.random.nextInt(26)) + this.randomString(ADDRESS_CHARACTERS, ADDRESS_LENGTH - 1);
        Minecraft.getInstance().keyboardHandler.setClipboard(this.address);
        VFPScreen.showToast(Component.translatable("bedrock_create_account.viafabricplus.address_copied"));
    }

    private void copyPassword() {
        this.password = this.randomString(PASSWORD_CHARACTERS, PASSWORD_LENGTH);
        Minecraft.getInstance().keyboardHandler.setClipboard(this.password);
        VFPScreen.showToast(Component.translatable("bedrock_create_account.viafabricplus.password_copied"));
    }

    private String randomString(final String characters, final int length) {
        final StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(characters.charAt(this.random.nextInt(characters.length())));
        }
        return builder.toString();
    }

    private void logIn() {
        this.accountBeforeLogin = ViaFabricPlusBedrock.impl().account().get();
        this.loggingIn = true;
        ViaFabricPlusBedrock.impl().account().login(true);
    }

}
