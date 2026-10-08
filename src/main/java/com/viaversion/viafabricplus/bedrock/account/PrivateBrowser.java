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

import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.screen.base.VFPScreen;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * Opens pages in a private window of the default browser, so accounts made for testing don't sign in next to the
 * player's own accounts there.
 */
public final class PrivateBrowser {

    private PrivateBrowser() {
    }

    /**
     * Opens the page in a private window. When the default browser isn't known, the link is copied to paste into one
     * instead, rather than opening it next to the player's own accounts.
     */
    public static void open(final String url) {
        Thread.ofVirtual().name("ViaFabricPlus Bedrock private window").start(() -> {
            boolean opened = false;
            try {
                final List<String> command = command(url);
                if (command != null) {
                    new ProcessBuilder(command).start();
                    opened = true;
                }
            } catch (final Exception e) {
                ViaFabricPlusBedrock.impl().logger().error("Failed to open a private browser window", e);
            }
            if (!opened) {
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(url);
                    VFPScreen.showToast(Component.translatable("bedrock_create_account.viafabricplus.paste_private"));
                });
            }
        });
    }

    private static @Nullable List<String> command(final String url) throws IOException, InterruptedException {
        return switch (Util.getPlatform()) {
            case WINDOWS -> {
                final String browser = windowsBrowser();
                final String flag = browser != null ? privateFlag(Path.of(browser).getFileName().toString()) : null;
                yield flag != null ? List.of(browser, flag, url) : null;
            }
            case LINUX -> {
                // The desktop entry is usually named after the command, like firefox.desktop or google-chrome.desktop
                final String entry = run("xdg-settings", "get", "default-web-browser").trim();
                final String browser = entry.endsWith(".desktop") ? entry.substring(0, entry.length() - ".desktop".length()) : entry;
                final String flag = privateFlag(browser);
                yield flag != null && !browser.isEmpty() ? List.of(browser, flag, url) : null;
            }
            default -> null;
        };
    }

    /**
     * @return the browser's executable, from the program Windows opens https links with
     */
    private static @Nullable String windowsBrowser() throws IOException, InterruptedException {
        final String progId = registryValue(run("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\Shell\\Associations\\UrlAssociations\\https\\UserChoice", "/v", "ProgId"));
        if (progId == null) {
            return null;
        }
        final String openCommand = registryValue(run("reg", "query", "HKCR\\" + progId + "\\shell\\open\\command", "/ve"));
        if (openCommand == null) {
            return null;
        }
        final String executable;
        if (openCommand.startsWith("\"")) {
            executable = openCommand.substring(1, openCommand.indexOf('"', 1));
        } else {
            final int end = openCommand.toLowerCase(Locale.ROOT).indexOf(".exe");
            executable = end < 0 ? null : openCommand.substring(0, end + 4);
        }
        return executable != null && Files.isRegularFile(Path.of(executable)) ? executable : null;
    }

    private static @Nullable String privateFlag(final String browser) {
        final String name = browser.toLowerCase(Locale.ROOT);
        if (name.contains("firefox") || name.contains("librewolf") || name.contains("waterfox") || name.contains("floorp") || name.contains("zen")) {
            return "-private-window";
        } else if (name.contains("msedge") || name.contains("microsoft-edge")) {
            return "--inprivate";
        } else if (name.contains("opera")) {
            return "--private";
        } else if (name.contains("chrome") || name.contains("chromium") || name.contains("brave") || name.contains("vivaldi") || name.contains("thorium")) {
            return "--incognito";
        }
        return null;
    }

    /**
     * @return the value of the line reg query printed for it, like "    ProgId    REG_SZ    ChromeHTML"
     */
    private static @Nullable String registryValue(final String output) {
        for (final String line : output.split("\\R")) {
            final int type = line.indexOf("REG_SZ");
            if (type >= 0) {
                return line.substring(type + "REG_SZ".length()).trim();
            }
        }
        return null;
    }

    private static String run(final String... command) throws IOException, InterruptedException {
        final Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        final String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor(5, TimeUnit.SECONDS);
        return output;
    }

}
