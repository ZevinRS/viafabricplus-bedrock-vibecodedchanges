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

import com.viaversion.viafabricplus.bedrock.screen.form.BedrockFormButton;
import com.viaversion.viafabricplus.bedrock.screen.form.BedrockForms;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.dialog.DialogControlSet;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.action.Action;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DialogControlSet.class)
public abstract class MixinDialogControlSet {

    @Shadow
    @Final
    private DialogScreen<?> screen;

    @Shadow
    public abstract Supplier<Optional<ClickEvent>> bindAction(final Optional<Action> maybeAction);

    @Inject(method = "createActionButton", at = @At("HEAD"), cancellable = true)
    private void createBedrockFormButton(final ActionButton actionButton, final CallbackInfoReturnable<Button.Builder> cir) {
        if (!BedrockForms.isActive()) {
            return;
        }

        final CommonButtonData data = actionButton.button();
        final Supplier<Optional<ClickEvent>> action = this.bindAction(actionButton.action());
        final int width = Math.max(data.width(), BedrockForms.width(this.screen.width));
        cir.setReturnValue(new BedrockFormButton.Builder(data.label(), _ -> this.screen.runAction(action.get()), width, data.tooltip().orElse(null)));
    }

}
