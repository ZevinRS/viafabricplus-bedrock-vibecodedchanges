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

import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.Nullable;
import team.unnamed.mocha.runtime.Scope;

/**
 * A Bedrock animation: how it loops, and the rotation, position and scale it gives bones over time.
 *
 * @param length        in seconds, the last keyframe's time if the file has none
 * @param animTimeUpdate how its time advances each frame instead of by the frame's duration, or null
 * @param bones          by lowercase bone name
 */
public record BedrockAnimation(Loop loop, double length, @Nullable String animTimeUpdate, @Nullable BedrockMoLang.Value startDelay,
                               Map<String, Bone> bones) {

    public enum Loop {
        ONCE, LOOP, HOLD_ON_LAST_FRAME
    }

    /**
     * @param channels rotation, position and scale, each null if the animation doesn't change it
     */
    public record Bone(@Nullable Channel rotation, @Nullable Channel position, @Nullable Channel scale) {
    }

    /**
     * A fixed value, or keyframes sorted by time.
     */
    public record Channel(BedrockMoLang.Value @Nullable [] fixed, List<Keyframe> keyframes) {

        /**
         * @param time the animation's time, with q.anim_time already set in the scope
         */
        public double[] evaluate(final Scope scope, final double time) {
            if (this.fixed != null) {
                return evaluate(this.fixed, scope);
            }
            final List<Keyframe> frames = this.keyframes;
            if (time <= frames.getFirst().time()) {
                return evaluate(frames.getFirst().pre(), scope);
            }
            if (time >= frames.getLast().time()) {
                return evaluate(frames.getLast().post(), scope);
            }
            int next = 1;
            while (frames.get(next).time() <= time) {
                next++;
            }
            final Keyframe from = frames.get(next - 1);
            final Keyframe to = frames.get(next);
            final double alpha = (time - from.time()) / (to.time() - from.time());
            final double[] a = evaluate(from.post(), scope);
            final double[] b = evaluate(to.pre(), scope);
            if (from.catmullRom() || to.catmullRom()) {
                final double[] before = next >= 2 ? evaluate(frames.get(next - 2).post(), scope) : a;
                final double[] after = next + 1 < frames.size() ? evaluate(frames.get(next + 1).pre(), scope) : b;
                final double[] value = new double[3];
                for (int i = 0; i < 3; i++) {
                    value[i] = catmullRom(before[i], a[i], b[i], after[i], alpha);
                }
                return value;
            }
            return new double[]{a[0] + (b[0] - a[0]) * alpha, a[1] + (b[1] - a[1]) * alpha, a[2] + (b[2] - a[2]) * alpha};
        }

        private static double[] evaluate(final BedrockMoLang.Value[] values, final Scope scope) {
            return new double[]{values[0].evaluate(scope), values[1].evaluate(scope), values[2].evaluate(scope)};
        }

        private static double catmullRom(final double p0, final double p1, final double p2, final double p3, final double t) {
            final double t2 = t * t;
            final double t3 = t2 * t;
            return 0.5 * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (3 * p1 - p0 - 3 * p2 + p3) * t3);
        }

    }

    public record Keyframe(double time, BedrockMoLang.Value[] pre, BedrockMoLang.Value[] post, boolean catmullRom) {
    }

    public static BedrockAnimation parse(final JsonObject animation) {
        final Loop loop;
        final JsonElement loopValue = animation.get("loop");
        if (loopValue != null && loopValue.isJsonPrimitive() && loopValue.getAsJsonPrimitive().isString()) {
            loop = loopValue.getAsString().equals("hold_on_last_frame") ? Loop.HOLD_ON_LAST_FRAME : Loop.ONCE;
        } else {
            loop = loopValue != null && loopValue.isJsonPrimitive() && loopValue.getAsBoolean() ? Loop.LOOP : Loop.ONCE;
        }
        final Map<String, Bone> bones = new HashMap<>();
        double lastKeyframe = 0;
        if (animation.get("bones") instanceof final JsonObject boneObject) {
            for (final Map.Entry<String, JsonElement> bone : boneObject.entrySet()) {
                if (!(bone.getValue() instanceof final JsonObject channels)) {
                    continue;
                }
                final Channel rotation = channel(channels.get("rotation"), 0);
                final Channel position = channel(channels.get("position"), 0);
                final Channel scale = channel(channels.get("scale"), 1);
                for (final Channel channel : new Channel[]{rotation, position, scale}) {
                    if (channel != null && !channel.keyframes().isEmpty()) {
                        lastKeyframe = Math.max(lastKeyframe, channel.keyframes().getLast().time());
                    }
                }
                bones.put(bone.getKey().toLowerCase(Locale.ROOT), new Bone(rotation, position, scale));
            }
        }
        final double length = animation.get("animation_length") instanceof final JsonElement value && value.isJsonPrimitive() ? value.getAsDouble() : lastKeyframe;
        final String animTimeUpdate = animation.get("anim_time_update") instanceof final JsonElement value && value.isJsonPrimitive() ? value.getAsString() : null;
        final BedrockMoLang.Value startDelay = animation.has("start_delay") ? BedrockMoLang.Value.of(animation.get("start_delay")) : null;
        return new BedrockAnimation(loop, length, animTimeUpdate, startDelay, bones);
    }

    /**
     * @param fallback the value of axes a single number doesn't give
     */
    private static @Nullable Channel channel(final @Nullable JsonElement element, final double fallback) {
        if (element == null) {
            return null;
        }
        if (element instanceof final JsonObject keyframeObject && !keyframeObject.has("vector")) {
            final List<Keyframe> keyframes = new ArrayList<>();
            for (final Map.Entry<String, JsonElement> keyframe : keyframeObject.entrySet()) {
                final double time;
                try {
                    time = Double.parseDouble(keyframe.getKey());
                } catch (final NumberFormatException e) {
                    continue;
                }
                if (keyframe.getValue() instanceof final JsonObject frame && !frame.has("vector")) {
                    final BedrockMoLang.Value[] post = vector(frame.has("post") ? frame.get("post") : frame.get("pre"), fallback);
                    final BedrockMoLang.Value[] pre = frame.has("pre") ? vector(frame.get("pre"), fallback) : post;
                    final boolean catmullRom = frame.get("lerp_mode") instanceof final JsonElement mode && mode.isJsonPrimitive() && mode.getAsString().equals("catmullrom");
                    keyframes.add(new Keyframe(time, pre, post, catmullRom));
                } else {
                    final BedrockMoLang.Value[] value = vector(keyframe.getValue(), fallback);
                    keyframes.add(new Keyframe(time, value, value, false));
                }
            }
            if (keyframes.isEmpty()) {
                return null;
            }
            keyframes.sort(Comparator.comparingDouble(Keyframe::time));
            return new Channel(null, keyframes);
        }
        return new Channel(vector(element, fallback), List.of());
    }

    private static BedrockMoLang.Value[] vector(final @Nullable JsonElement element, final double fallback) {
        JsonElement value = element;
        if (value instanceof final JsonObject object && object.has("vector")) {
            value = object.get("vector");
        }
        if (value instanceof final JsonArray array) {
            final BedrockMoLang.Value[] vector = new BedrockMoLang.Value[3];
            for (int i = 0; i < 3; i++) {
                vector[i] = i < array.size() ? BedrockMoLang.Value.of(array.get(i)) : new BedrockMoLang.Constant(fallback);
            }
            return vector;
        }
        // One value for all three axes
        final BedrockMoLang.Value single = value != null ? BedrockMoLang.Value.of(value) : new BedrockMoLang.Constant(fallback);
        return new BedrockMoLang.Value[]{single, single, single};
    }

}
