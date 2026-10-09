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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.entity;

import com.viaversion.viafabricplus.bedrock.injection.access.IEntityRenderState;
import java.util.List;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public abstract class MixinEntityRenderState implements IEntityRenderState {

    @Unique
    private @Nullable List<Component> viaFabricPlusBedrock$nameLines;

    @Unique
    private boolean viaFabricPlusBedrock$bodyHidden;

    @Override
    public @Nullable List<Component> viaFabricPlusBedrock$getNameLines() {
        return this.viaFabricPlusBedrock$nameLines;
    }

    @Override
    public void viaFabricPlusBedrock$setNameLines(final @Nullable List<Component> lines) {
        this.viaFabricPlusBedrock$nameLines = lines;
    }

    @Override
    public boolean viaFabricPlusBedrock$isBodyHidden() {
        return this.viaFabricPlusBedrock$bodyHidden;
    }

    @Override
    public void viaFabricPlusBedrock$setBodyHidden(final boolean hidden) {
        this.viaFabricPlusBedrock$bodyHidden = hidden;
    }

}
