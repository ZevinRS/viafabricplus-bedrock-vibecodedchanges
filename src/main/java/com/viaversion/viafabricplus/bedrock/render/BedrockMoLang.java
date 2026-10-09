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

import com.viaversion.viaversion.libs.gson.JsonElement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.raphimc.viabedrock.api.util.MoLangEngine;
import org.jetbrains.annotations.Nullable;
import team.unnamed.mocha.parser.ast.Expression;
import team.unnamed.mocha.runtime.Scope;

/**
 * MoLang expressions of animations, parsed once.
 */
public final class BedrockMoLang {

    private static final Map<String, List<Expression>> PARSED = new ConcurrentHashMap<>();
    private static final List<Expression> INVALID = List.of();

    private BedrockMoLang() {
    }

    /**
     * A number or an expression from a pack file.
     */
    public sealed interface Value permits Constant, Parsed {

        static Value of(final @Nullable JsonElement element) {
            if (element == null || !element.isJsonPrimitive()) {
                return Constant.ZERO;
            }
            if (element.getAsJsonPrimitive().isNumber()) {
                return new Constant(element.getAsDouble());
            }
            if (element.getAsJsonPrimitive().isBoolean()) {
                return new Constant(element.getAsBoolean() ? 1 : 0);
            }
            return of(element.getAsString());
        }

        static Value of(final String expression) {
            try {
                return new Constant(Double.parseDouble(expression.trim()));
            } catch (final NumberFormatException e) {
                return expression.isBlank() ? Constant.ZERO : new Parsed(expression);
            }
        }

        double evaluate(Scope scope);

    }

    public record Constant(double value) implements Value {

        static final Constant ZERO = new Constant(0);

        @Override
        public double evaluate(final Scope scope) {
            return this.value;
        }

    }

    public record Parsed(String expression) implements Value {

        @Override
        public double evaluate(final Scope scope) {
            return number(scope, this.expression);
        }

    }

    public static double number(final Scope scope, final String expression) {
        final team.unnamed.mocha.runtime.value.Value value = run(scope, expression);
        return value != null ? value.getAsNumber() : 0;
    }

    /**
     * Runs an expression, like a script setting variables.
     *
     * @return its value, or null if it isn't valid MoLang
     */
    public static team.unnamed.mocha.runtime.value.@Nullable Value run(final Scope scope, final String expression) {
        final List<Expression> parsed = PARSED.computeIfAbsent(expression, e -> {
            try {
                return MoLangEngine.parse(e);
            } catch (final Throwable t) {
                return INVALID;
            }
        });
        if (parsed == INVALID) {
            return null;
        }
        try {
            return MoLangEngine.eval(scope, parsed);
        } catch (final Throwable t) {
            return null;
        }
    }

}
