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

package com.viaversion.viafabricplus.bedrock.injection.mixin.viabedrock;

import com.viaversion.viafabricplus.bedrock.feature.Features;
import com.viaversion.viafabricplus.bedrock.building.BedrockSprint;
import com.viaversion.viaversion.api.minecraft.entitydata.EntityData;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import net.raphimc.viabedrock.api.model.entity.ClientPlayerEntity;
import net.raphimc.viabedrock.api.model.entity.LivingEntity;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.AttributeModifierOperation;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.AttributeOperands;
import net.raphimc.viabedrock.protocol.data.generated.java.Attributes;
import net.raphimc.viabedrock.protocol.model.EntityAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, remap = false)
public abstract class MixinLivingEntity {

    private static final String BEDROCK_SPRINT_MODIFIER = "Sprinting speed boost";
    // Java's sprint modifier, which the client adds and removes itself when it starts and stops sprinting
    private static final String JAVA_SPRINT_MODIFIER = "minecraft:sprinting";
    private static final double JAVA_SPRINT_AMOUNT = 0.3;
    private static final int JAVA_ADD_VALUE = 0;
    private static final int JAVA_ADD_MULTIPLIED_BASE = 1;
    private static final int JAVA_ADD_MULTIPLIED_TOTAL = 2;
    private static final double MIN_SERVER_VALUE_DIFFERENCE = 1.0E-6;

    // The client player's movement speed as the server sent it last
    @Unique
    private EntityAttribute viaFabricPlusBedrock$lastMovement;

    /**
     * Bedrock keeps the client player's speed as base value, modifiers and the current value the server sent, which can
     * differ from what the modifiers give (Dragonfly sends the sprinting speed as current value without modifiers). The
     * client computes the current value from the base value again whenever its own sprint modifier is added or removed.
     * ViaBedrock sends the current value as Java base value, so a sprint start and stop in the same tick kept the
     * server's speed instead of going back to the base value. So the base value and the modifiers are sent as they are,
     * the sprint boost as Java's sprint modifier, and the difference to the current value as a modifier of its own,
     * which is dropped when Bedrock would compute the value again (see {@link BedrockSprint#SERVER_VALUE_MODIFIER}).
     */
    @Inject(method = "translateAttribute", at = @At("HEAD"), cancellable = true)
    private void translateClientPlayerSpeed(final EntityAttribute attribute, final PacketWrapper javaAttributes, final AtomicInteger attributeCount,
                                            final List<EntityData> javaEntityData, final CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ClientPlayerEntity) || !attribute.name().equals("minecraft:movement") || !Features.SPRINT.isEnabled()) {
            return;
        }
        // Bedrock only acts on a changed value. Dragonfly sends the speed again with every other attribute, which on Java
        // would replace the sprint modifier the client added since, as recorded when a hunger update came right after
        // a sprint start.
        if (viaFabricPlusBedrock$sameValue(attribute, this.viaFabricPlusBedrock$lastMovement)) {
            cir.setReturnValue(true);
            return;
        }
        this.viaFabricPlusBedrock$lastMovement = attribute;
        final double base = attribute.defaultValue();
        boolean sprinting = false;
        double add = 0;
        double multiplyBase = 0;
        double multiplyTotal = 1;
        final List<JavaModifier> modifiers = new ArrayList<>();
        for (final EntityAttribute.Modifier modifier : attribute.modifiers()) {
            if (modifier.operand() != AttributeOperands.OPERAND_CURRENT) {
                continue;
            }
            if (modifier.name().equals(BEDROCK_SPRINT_MODIFIER) && modifier.operation() == AttributeModifierOperation.OPERATION_MULTIPLY_TOTAL) {
                sprinting = true;
                multiplyTotal *= 1 + JAVA_SPRINT_AMOUNT;
                continue;
            }
            final int operation;
            if (modifier.operation() == AttributeModifierOperation.OPERATION_ADDITION) {
                operation = JAVA_ADD_VALUE;
                add += modifier.amount();
            } else if (modifier.operation() == AttributeModifierOperation.OPERATION_MULTIPLY_BASE) {
                operation = JAVA_ADD_MULTIPLIED_BASE;
                multiplyBase += modifier.amount();
            } else if (modifier.operation() == AttributeModifierOperation.OPERATION_MULTIPLY_TOTAL) {
                operation = JAVA_ADD_MULTIPLIED_TOTAL;
                multiplyTotal *= 1 + modifier.amount();
            } else {
                continue; // Caps end up in the difference to the current value
            }
            final String id = "viafabricplus_bedrock:" + modifier.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
            modifiers.add(new JavaModifier(id, modifier.amount(), operation));
        }
        // Java adds values first and multiplies after, so the difference is added before the multipliers
        final double serverValue = attribute.computeClampedValue() / ((1 + multiplyBase) * multiplyTotal) - (base + add);
        if (Math.abs(serverValue) > MIN_SERVER_VALUE_DIFFERENCE) {
            modifiers.add(new JavaModifier(BedrockSprint.SERVER_VALUE_MODIFIER.toString(), serverValue, JAVA_ADD_VALUE));
        }
        if (sprinting) {
            modifiers.add(new JavaModifier(JAVA_SPRINT_MODIFIER, JAVA_SPRINT_AMOUNT, JAVA_ADD_MULTIPLIED_TOTAL));
        }

        javaAttributes.write(Types.VAR_INT, BedrockProtocol.MAPPINGS.getJavaEntityAttributes().get(Attributes.MOVEMENT_SPEED)); // attribute id
        javaAttributes.write(Types.DOUBLE, base); // base value
        javaAttributes.write(Types.VAR_INT, modifiers.size()); // modifier count
        for (final JavaModifier modifier : modifiers) {
            javaAttributes.write(Types.STRING, modifier.id()); // id
            javaAttributes.write(Types.DOUBLE, modifier.amount()); // amount
            javaAttributes.write(Types.VAR_INT, modifier.operation()); // operation
        }
        attributeCount.incrementAndGet();
        cir.setReturnValue(true);
    }

    @Unique
    private static boolean viaFabricPlusBedrock$sameValue(final EntityAttribute attribute, final EntityAttribute last) {
        return last != null && attribute.currentValue() == last.currentValue() && attribute.defaultValue() == last.defaultValue()
            && attribute.minValue() == last.minValue() && attribute.maxValue() == last.maxValue() && Arrays.equals(attribute.modifiers(), last.modifiers());
    }

    private record JavaModifier(String id, double amount, int operation) {
    }

}
