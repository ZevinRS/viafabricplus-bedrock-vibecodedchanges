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

import com.viaversion.viafabricplus.bedrock.account.BrowserCodeMsaAuthService;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Waits for the Microsoft sign-in in the browser, which ends on a blank page with the code in its address. Copying the
 * address is enough, it's picked up from the clipboard; it can also be pasted.
 */
public final class BedrockSignInScreen extends VFPScreen {

    private static final int CLIPBOARD_CHECK_TICKS = 10;
    private static final int SECONDARY_COLOR = 0xFFB8B8B8;

    private final BrowserCodeMsaAuthService service;
    private final String signInAddress;
    // What the clipboard held when the screen opened, so the address of an earlier sign-in isn't used again
    private String lastClipboard;
    private Component status = Component.translatable("bedrock_sign_in.viafabricplus.browser");
    private boolean done;
    private int ticks;
    private EditBox addressField;

    public BedrockSignInScreen(final BrowserCodeMsaAuthService service, final String signInAddress) {
        super(Component.translatable("screen.viafabricplus.bedrock_sign_in"), true);
        this.service = service;
        this.signInAddress = signInAddress;
    }

    @Override
    protected void init() {
        if (this.lastClipboard == null) {
            this.lastClipboard = this.minecraft.keyboardHandler.getClipboard();
        }
        final int width = Math.min(300, this.width - 40);
        this.addressField = this.addRenderableWidget(new EditBox(this.font, (this.width - width) / 2, this.height / 2 + 4, width, 20,
            Component.translatable("bedrock_sign_in.viafabricplus.paste")));
        this.addressField.setMaxLength(4096);
        this.addressField.setHint(Component.translatable("bedrock_sign_in.viafabricplus.paste"));
        this.addressField.setResponder(this::tryAddress);
        this.addressField.active = !this.done;
        this.addFooter(Button.builder(Component.translatable("base.viafabricplus.copy_link"),
            _ -> this.minecraft.keyboardHandler.setClipboard(this.signInAddress)).build());
        super.init();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.done && ++this.ticks % CLIPBOARD_CHECK_TICKS == 0) {
            final String clipboard = this.minecraft.keyboardHandler.getClipboard();
            if (!clipboard.equals(this.lastClipboard)) {
                this.lastClipboard = clipboard;
                this.tryAddress(clipboard);
            }
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
        graphics.centeredText(this.font, this.status, this.width / 2, this.height / 2 - 30, -1);
        if (!this.done) {
            graphics.centeredText(this.font, Component.translatable("bedrock_sign_in.viafabricplus.copy_address"), this.width / 2,
                this.height / 2 - 16, SECONDARY_COLOR);
        }
    }

    @Override
    public void onClose() {
        if (!this.done) {
            this.service.cancel();
        }
        super.onClose();
    }

    public void setStatus(final Component status) {
        this.status = status;
    }

    private void tryAddress(final String address) {
        if (!this.done && this.service.complete(address)) {
            this.done = true;
            this.status = Component.translatable("bedrock_sign_in.viafabricplus.signing_in");
            if (this.addressField != null) {
                this.addressField.active = false;
            }
        }
    }

}
