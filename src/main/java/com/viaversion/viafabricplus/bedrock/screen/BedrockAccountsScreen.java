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
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.screen.base.list.VFPList;
import com.viaversion.viafabricplus.screen.base.list.VFPListEntry;
import com.viaversion.viafabricplus.screen.base.list.VFPTextEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/** The saved Bedrock accounts, to switch between them, log in to another one or remove one. */
public final class BedrockAccountsScreen extends VFPScreen {

    public static final Component TITLE = Component.translatable("screen.viafabricplus.bedrock_accounts");

    private static final int ROW_WIDTH = 300;
    private static final int SECONDARY_COLOR = 0xFFB8B8B8;

    private AccountList list;
    private Button useButton;
    private Button removeButton;

    public BedrockAccountsScreen() {
        super(TITLE, true);
    }

    @Override
    protected void init() {
        this.list = this.addRenderableWidget(new AccountList(this.minecraft, this.width, this.height, CONTENT_TOP, FOOTER_HEIGHT,
            this.font.lineHeight * 2 + 12));
        this.useButton = Button.builder(Component.translatable("bedrock_accounts.viafabricplus.use"), _ -> this.useSelected()).build();
        final Button addButton = Button.builder(Component.translatable("bedrock_accounts.viafabricplus.add"),
            _ -> ViaFabricPlusBedrock.impl().account().login()).build();
        this.removeButton = Button.builder(Component.translatable("bedrock_accounts.viafabricplus.remove"), _ -> this.confirmRemove()).build();
        final Button createButton = Button.builder(Component.translatable("bedrock_accounts.viafabricplus.create"),
            _ -> new BedrockCreateAccountScreen().open(this)).build();
        this.addFooter(this.useButton, addButton, createButton, this.removeButton);
        super.init();
    }

    @Override
    public void tick() {
        super.tick();
        final BedrockAuthManager account = this.selectedAccount();
        this.useButton.active = account != null && account != ViaFabricPlusBedrock.impl().account().get();
        this.removeButton.active = account != null;
    }

    @Override
    public void renderTitle(final GuiGraphicsExtractor graphics) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(2F, 2F);
        graphics.centeredText(this.font, this.title, this.width / 4, 6, ACCENT_COLOR);
        graphics.pose().popMatrix();
    }

    private @Nullable BedrockAuthManager selectedAccount() {
        return this.list.getFocused() instanceof AccountEntry entry ? entry.account : null;
    }

    private void useSelected() {
        final BedrockAuthManager account = this.selectedAccount();
        if (account != null) {
            ViaFabricPlusBedrock.impl().account().select(account);
            VFPScreen.showToast(Component.translatable("bedrock_accounts.viafabricplus.switched", name(account)));
        }
    }

    private void confirmRemove() {
        final BedrockAuthManager account = this.selectedAccount();
        if (account == null) {
            return;
        }
        VFPScreen.setScreen(new ConfirmScreen(confirmed -> {
            if (confirmed) {
                ViaFabricPlusBedrock.impl().account().remove(account);
            }
            VFPScreen.setScreen(this);
        }, Component.translatable("bedrock_accounts.viafabricplus.remove_confirm", name(account)),
            Component.translatable("bedrock_accounts.viafabricplus.remove_notice"),
            Component.translatable("bedrock_accounts.viafabricplus.remove"), Component.translatable("base.viafabricplus.cancel")));
    }

    private static String name(final BedrockAuthManager account) {
        final String name = BedrockAccount.displayName(account);
        return name != null ? name : Component.translatable("bedrock_accounts.viafabricplus.unknown").getString();
    }

    private final class AccountList extends VFPList {

        private AccountList(final Minecraft minecraft, final int width, final int height, final int top, final int bottom, final int entryHeight) {
            super(minecraft, width, height, top, bottom, entryHeight);
            final BedrockAccount accounts = ViaFabricPlusBedrock.impl().account();
            if (accounts.accounts().isEmpty()) {
                this.addEntry(new VFPTextEntry(Component.translatable("bedrock_accounts.viafabricplus.empty")));
                return;
            }
            for (final BedrockAuthManager account : accounts.accounts()) {
                final AccountEntry entry = new AccountEntry(this, account);
                this.addEntry(entry);
                if (account == accounts.get()) {
                    this.setFocused(entry);
                }
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(ROW_WIDTH, this.width - 20);
        }

    }

    private final class AccountEntry extends VFPListEntry {

        private final AccountList list;
        private final BedrockAuthManager account;

        private AccountEntry(final AccountList list, final BedrockAuthManager account) {
            this.list = list;
            this.account = account;
        }

        @Override
        public @NonNull Component getNarration() {
            return Component.literal(name(this.account));
        }

        @Override
        public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
            final boolean handled = super.mouseClicked(event, doubleClick);
            if (doubleClick) {
                BedrockAccountsScreen.this.useSelected();
            }
            return handled;
        }

        @Override
        public void mappedRender(final GuiGraphicsExtractor graphics, final int entryWidth, final int entryHeight) {
            final Font font = Minecraft.getInstance().font;
            final boolean inUse = this.account == ViaFabricPlusBedrock.impl().account().get();
            if (this.list.getFocused() == this) {
                graphics.fill(0, 0, 2, entryHeight, ACCENT_COLOR);
            }
            graphics.text(font, name(this.account), SLOT_MARGIN, SLOT_MARGIN + 1, this.list.getFocused() == this ? ACCENT_COLOR : -1);
            if (inUse) {
                final Component status = Component.translatable("bedrock_accounts.viafabricplus.in_use");
                graphics.text(font, status, entryWidth - font.width(status) - SLOT_MARGIN, SLOT_MARGIN + 1, ACCENT_COLOR);
            }
            final String xuid = this.account.getMinecraftMultiplayerToken().hasValue() ? this.account.getMinecraftMultiplayerToken().getCached().getXuid() : null;
            if (xuid != null) {
                graphics.text(font, "XUID " + xuid, SLOT_MARGIN, SLOT_MARGIN + font.lineHeight + 4, SECONDARY_COLOR);
            }
        }

    }

}
