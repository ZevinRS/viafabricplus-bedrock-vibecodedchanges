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

package com.viaversion.viafabricplus.bedrock.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.Blaze3D;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.injection.access.IConfirmScreen;
import com.viaversion.viafabricplus.bedrock.friends.BedrockFriendsService;
import com.viaversion.viafabricplus.bedrock.screen.BedrockRealmsScreen;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import com.viaversion.viafabricplus.util.JsonSave;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import net.raphimc.minecraftauth.util.holder.listener.ChangeListener;
import net.raphimc.minecraftauth.xbl.exception.XblRequestException;
import net.raphimc.viabedrock.protocol.data.ProtocolConstants;
import org.jetbrains.annotations.Nullable;

/**
 * The Bedrock accounts the player logged in to, one of which is used to join servers.
 */
public final class BedrockAccount {

    private static final Component TITLE = Component.nullToEmpty("Microsoft Bedrock login");
    // Signing in to Xbox with a new account asks for a gamertag and creates its Xbox profile
    public static final String XBOX_SIGN_IN = "https://www.xbox.com/en-US/auth/msa?action=logIn&returnUrl=https%3A%2F%2Fwww.xbox.com%2Fen-US%2F";

    private final Path path;
    private final List<BedrockAuthManager> accounts = new CopyOnWriteArrayList<>();
    private volatile @Nullable BedrockAuthManager selected;
    private Thread thread;

    /**
     * @param legacyPath the single account older versions saved, imported once
     */
    public BedrockAccount(final Path path, final Path legacyPath) {
        this.path = path;
        JsonSave.load(path, object -> {
            for (final JsonElement element : object.getAsJsonArray("accounts")) {
                this.accounts.add(this.load(element.getAsJsonObject()));
            }
            final int selected = object.has("selected") ? object.get("selected").getAsInt() : 0;
            this.selected = selected >= 0 && selected < this.accounts.size() ? this.accounts.get(selected) : null;
        }, this::serialize);
        if (!Files.exists(path) && Files.exists(legacyPath)) {
            JsonSave.read(legacyPath, object -> {
                this.selected = this.load(object);
                this.accounts.add(this.selected);
            });
            this.save();
        }
    }

    private BedrockAuthManager load(final JsonObject object) {
        final BedrockAuthManager account = BedrockAuthManager.fromJson(MinecraftAuth.createHttpClient(), ProtocolConstants.BEDROCK_VERSION_NAME, object);
        account.getChangeListeners().add(this::save);
        return account;
    }

    private JsonObject serialize() {
        final JsonObject object = new JsonObject();
        final JsonArray accounts = new JsonArray();
        this.accounts.forEach(account -> accounts.add(BedrockAuthManager.toJson(account)));
        object.add("accounts", accounts);
        object.addProperty("selected", this.accounts.indexOf(this.selected));
        return object;
    }

    /**
     * Writes the accounts right away instead of only in the shutdown hook, so logins and refreshed tokens
     * aren't lost when the game is killed or crashes.
     */
    private synchronized void save() {
        JsonSave.write(this.path, this::serialize);
    }

    /**
     * @return the account used to join servers
     */
    public @Nullable BedrockAuthManager get() {
        return this.selected;
    }

    public List<BedrockAuthManager> accounts() {
        return Collections.unmodifiableList(this.accounts);
    }

    public synchronized void select(final BedrockAuthManager account) {
        if (this.selected != account && this.accounts.contains(account)) {
            this.selected = account;
            this.save();
            switched();
        }
    }

    public synchronized void remove(final BedrockAuthManager account) {
        this.accounts.remove(account);
        if (this.selected == account) {
            this.selected = this.accounts.isEmpty() ? null : this.accounts.getFirst();
            switched();
        }
        this.save();
    }

    public @Nullable String displayName() {
        return this.selected != null ? displayName(this.selected) : null;
    }

    public static @Nullable String displayName(final BedrockAuthManager account) {
        return account.getMinecraftMultiplayerToken().hasValue() ? account.getMinecraftMultiplayerToken().getCached().getDisplayName() : null;
    }

    private static @Nullable String xuid(final BedrockAuthManager account) {
        return account.getMinecraftMultiplayerToken().hasValue() ? account.getMinecraftMultiplayerToken().getCached().getXuid() : null;
    }

    private static void switched() {
        BedrockFriendsService.leaveCurrent();
        BedrockRealmsScreen.invalidate(); // The realms of the previous account no longer apply
    }

    /**
     * Saves a new login and uses it. Logging in to an account again replaces its saved login.
     */
    private synchronized void add(final BedrockAuthManager account) {
        final String xuid = xuid(account);
        final int existing = xuid == null ? -1 : this.accounts.stream().map(BedrockAccount::xuid).toList().indexOf(xuid);
        if (existing >= 0) {
            this.accounts.set(existing, account);
        } else {
            this.accounts.add(account);
        }
        account.getChangeListeners().add(this::save);
        this.selected = account;
        this.save();
        switched();
    }

    public void login() {
        this.thread = new Thread(this::performLogin, "ViaFabricPlus Bedrock login");
        this.thread.start();
    }

    private void performLogin() {
        final Minecraft client = Minecraft.getInstance();
        final Screen prevScreen = client.gui.screen();
        try {
            final BedrockAuthManager account = BedrockAuthManager
                .create(MinecraftAuth.createHttpClient(), ProtocolConstants.BEDROCK_VERSION_NAME)
                .login(DeviceCodeMsaAuthService::new, (Consumer<MsaDeviceCode>) deviceCode -> {
                    VFPScreen.setScreen(new ConfirmScreen(copyUrl -> {
                        if (copyUrl) {
                            client.keyboardHandler.setClipboard(deviceCode.getDirectVerificationUri());
                        } else {
                            client.gui.setScreen(prevScreen);
                            this.thread.interrupt();
                        }
                    }, TITLE, Component.translatable("bedrock_account.viafabricplus.notice"), Component.translatable("base.viafabricplus.copy_link"), Component.translatable("base.viafabricplus.cancel")));
                    Blaze3D.openUri(URI.create(deviceCode.getDirectVerificationUri()));
                });
            account.getChangeListeners().add(new ChangeListener() {
                @Override
                public <T> void onChange(final T oldValue, final T newValue) {
                    updateLoginStatus(account, newValue);
                }
            });
            account.getMinecraftMultiplayerToken().refreshIfExpired();
            account.getMinecraftCertificateChain().refreshIfExpired();
            this.add(account);

            VFPScreen.setScreen(prevScreen);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) {
                return;
            }

            this.thread.interrupt();
            ViaFabricPlusBedrock.impl().logger().error("Failed to log in to the Bedrock account!", e);
            final XblRequestException xboxError = xboxError(e);
            if (xboxError != null && xboxError.getErrorCode() == XblRequestException.XO_E_ACCOUNT_CREATION_REQUIRED) {
                // A new Microsoft account has no Xbox profile until it signed in to Xbox once and picked a gamertag
                VFPScreen.setScreen(new ConfirmScreen(openXbox -> {
                    if (openXbox) {
                        Blaze3D.openUri(URI.create(XBOX_SIGN_IN));
                    }
                    client.gui.setScreen(prevScreen);
                }, Component.translatable("bedrock_accounts.viafabricplus.no_xbox_profile"),
                    Component.translatable("bedrock_accounts.viafabricplus.no_xbox_profile_notice"),
                    Component.translatable("bedrock_accounts.viafabricplus.open_xbox"), Component.translatable("base.viafabricplus.cancel")));
                return;
            }
            VFPScreen.setScreen(prevScreen);
            VFPScreen.showToast(xboxError != null ? Component.literal(xboxError.getMessage())
                : Component.translatable("base.viafabricplus.something_went_wrong"));
        }
    }

    private static @Nullable XblRequestException xboxError(@Nullable Throwable throwable) {
        while (throwable != null && !(throwable instanceof XblRequestException)) {
            throwable = throwable.getCause();
        }
        return (XblRequestException) throwable;
    }

    private static void updateLoginStatus(final BedrockAuthManager account, final Object value) {
        final String step;
        if (value == account.getMsaToken().getCached()) {
            step = "msatoken";
        } else if (value == account.getXblDeviceToken().getCached()) {
            step = "xbldevicetoken";
        } else if (value == account.getXblUserToken().getCached()) {
            step = "xblusertoken";
        } else if (value == account.getXblTitleToken().getCached()) {
            step = "xbltitletoken";
        } else if (value == account.getBedrockXstsToken().getCached()) {
            step = "bedrockxststoken";
        } else if (value == account.getPlayFabXstsToken().getCached()) {
            step = "playfabxststoken";
        } else if (value == account.getRealmsXstsToken().getCached()) {
            step = "realmsxststoken";
        } else if (value == account.getPlayFabToken().getCached()) {
            step = "playfabtoken";
        } else if (value == account.getMinecraftSession().getCached()) {
            step = "minecraftsession";
        } else if (value == account.getMinecraftMultiplayerToken().getCached()) {
            step = "minecraftmultiplayertoken";
        } else if (value == account.getMinecraftCertificateChain().getCached()) {
            step = "minecraftcertificatechain";
        } else {
            return;
        }

        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().gui.screen() instanceof ConfirmScreen confirmScreen) {
                ((IConfirmScreen) confirmScreen).viaFabricPlusBedrock$updateMessage(Component.translatable("minecraftauth_library.viafabricplus." + step));
            }
        });
    }

}
