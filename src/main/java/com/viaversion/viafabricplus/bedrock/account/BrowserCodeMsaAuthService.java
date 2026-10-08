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

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.msa.model.MsaApplicationConfig;
import net.raphimc.minecraftauth.msa.model.MsaToken;
import net.raphimc.minecraftauth.msa.request.MsaAuthCodeTokenRequest;
import net.raphimc.minecraftauth.msa.service.MsaAuthService;
import org.jetbrains.annotations.Nullable;

/**
 * Signs in like Bedrock on phones: Microsoft's sign-in page redirects to its blank desktop page with a code in the
 * address, which is exchanged for the token. Unlike a device code, this skips entering a code and confirming the
 * sign-in on another device, which Microsoft adds more security checks to. The browser's final address has to be
 * handed back with {@link #complete(String)}, since that page only shows the code in its address.
 */
public final class BrowserCodeMsaAuthService extends MsaAuthService {

    private static final int TIMEOUT_MINUTES = 10;

    private final Consumer<String> openPage;
    private final CompletableFuture<String> code = new CompletableFuture<>();

    /**
     * @param openPage opens the sign-in page with the given address
     */
    public BrowserCodeMsaAuthService(final HttpClient httpClient, final MsaApplicationConfig applicationConfig, final Consumer<String> openPage) {
        super(httpClient, applicationConfig);
        this.openPage = openPage;
    }

    @Override
    public MsaToken acquireToken() throws IOException, InterruptedException, TimeoutException {
        final MsaApplicationConfig config = this.applicationConfig.getRedirectUri() != null ? this.applicationConfig
            : this.applicationConfig.withRedirectUri(this.applicationConfig.getEnvironment().getNativeClientUrl());
        final Map<String, String> parameters = new LinkedHashMap<>(config.getAuthCodeParameters());
        parameters.put("prompt", "select_account");
        this.openPage.accept(config.getEnvironment().getAuthorizeUrl() + "?" + parameters.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&")));

        final String code;
        try {
            code = this.code.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (final ExecutionException e) {
            throw e.getCause() instanceof IOException ioException ? ioException : new IOException(e.getCause());
        }
        return (MsaToken) this.httpClient.executeAndHandle(new MsaAuthCodeTokenRequest(config, code));
    }

    /**
     * @param address the address the browser ended up on after signing in
     * @return whether the address was the end of the sign-in, with a code or an error
     */
    public boolean complete(final String address) {
        final Map<String, String> query = redirectQuery(address);
        if (query == null) {
            return false;
        }
        if (query.containsKey("code")) {
            this.code.complete(query.get("code"));
        } else {
            this.code.completeExceptionally(new IOException("Microsoft sign-in failed: " + query.getOrDefault("error_description", query.get("error"))));
        }
        return true;
    }

    public void cancel() {
        this.code.cancel(false);
    }

    private @Nullable Map<String, String> redirectQuery(final String address) {
        final URI uri;
        try {
            uri = URI.create(address.trim());
        } catch (final IllegalArgumentException e) {
            return null;
        }
        final URI redirect = URI.create(this.applicationConfig.getEnvironment().getNativeClientUrl());
        if (uri.getRawQuery() == null || !redirect.getHost().equalsIgnoreCase(uri.getHost()) || !redirect.getPath().equals(uri.getPath())) {
            return null;
        }
        final Map<String, String> query = new HashMap<>();
        for (final String parameter : uri.getRawQuery().split("&")) {
            final int separator = parameter.indexOf('=');
            if (separator > 0) {
                query.put(parameter.substring(0, separator), URLDecoder.decode(parameter.substring(separator + 1), StandardCharsets.UTF_8));
            }
        }
        return query.containsKey("code") || query.containsKey("error") ? query : null;
    }

}
