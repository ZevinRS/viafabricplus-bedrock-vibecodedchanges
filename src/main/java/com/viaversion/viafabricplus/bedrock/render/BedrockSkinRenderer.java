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

package com.viaversion.viafabricplus.bedrock.render;

import com.viaversion.viafabricplus.bedrock.feature.Features;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.injection.access.IEntityRenderState;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jetbrains.annotations.Nullable;

/**
 * Draws players whose skin has its own geometry, 4D skins, with a player model of that geometry. It keeps Java's
 * player animations, layers and held items, which move the bones of the geometry named like Java's parts and the bones
 * in them. One renderer is made per skin when its player is first drawn.
 */
public final class BedrockSkinRenderer extends AvatarRenderer<AbstractClientPlayer> {

    private static @Nullable EntityRendererProvider.Context context;
    // Render thread only, renderers of the skins by the context they were made with
    private static final Map<BedrockSkins.Skin, BedrockSkinRenderer> RENDERERS = new WeakHashMap<>();

    private BedrockSkinRenderer(final EntityRendererProvider.Context context, final BedrockGeometry geometry, final boolean slim) {
        super(context, slim);
        this.model = new PlayerModel(BedrockModel.playerRoot(geometry, slim), slim);
    }

    /**
     * Called when Java makes its entity renderers, which renderers of skins are made with too.
     */
    public static void setContext(final EntityRendererProvider.Context newContext) {
        context = newContext;
        RENDERERS.clear();
    }

    /**
     * @return the renderer of a player's 4D skin, or null for Java's
     */
    public static @Nullable BedrockSkinRenderer of(final AbstractClientPlayer player) {
        if (context == null || !Features.SKIN_GEOMETRY.isActive() || !Features.PLAYER_SKINS.isEnabled()) {
            return null;
        }
        final BedrockSkins.Skin skin = BedrockSkins.get(player.getUUID());
        if (skin == null) {
            return null;
        }
        if (RENDERERS.containsKey(skin)) {
            return RENDERERS.get(skin);
        }
        BedrockSkinRenderer renderer = null;
        final BedrockGeometry geometry = skin.geometry();
        if (geometry != null) {
            try {
                renderer = new BedrockSkinRenderer(context, geometry, skin.slim());
            } catch (final RuntimeException e) {
                ViaFabricPlusBedrock.impl().logger().error("Failed to make the model of a Bedrock skin", e);
            }
        }
        RENDERERS.put(skin, renderer);
        return renderer;
    }

    /**
     * Remembers the renderer in the state, which Java looks the renderer up by when it draws.
     */
    @Override
    public void extractRenderState(final AbstractClientPlayer entity, final AvatarRenderState state, final float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        ((IEntityRenderState) state).viaFabricPlusBedrock$setSkinRenderer(this);
    }

}
