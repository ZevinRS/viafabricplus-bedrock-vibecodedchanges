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

import com.viaversion.viafabricplus.bedrock.resourcepack.BedrockPackIndex;
import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonElement;
import com.viaversion.viaversion.libs.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import team.unnamed.mocha.runtime.Scope;
import team.unnamed.mocha.runtime.binding.JavaObjectBinding;
import team.unnamed.mocha.runtime.standard.MochaMath;
import team.unnamed.mocha.runtime.value.MutableObjectBinding;
import team.unnamed.mocha.runtime.value.Value;

/**
 * Plays the animations of one entity like Bedrock: its scripts set variables, the animations of its animate list play
 * while their condition holds, and animation controllers switch between states that play animations of their own.
 * Every frame gives the pose of the bones all of them add up to.
 */
public final class BedrockAnimator {

    private static final Scope BASE_SCOPE = Scope.create();

    static {
        BASE_SCOPE.set("math", JavaObjectBinding.of(MochaMath.class, null, new MochaMath()));
    }

    private final BedrockPackIndex index;
    private final JsonObject description;
    private final MutableObjectBinding variables = new MutableObjectBinding();
    private final Map<String, Player> animate = new LinkedHashMap<>();
    private boolean initialized;
    private double lastTime = Double.NaN;

    public BedrockAnimator(final BedrockPackIndex index, final JsonObject description) {
        this.index = index;
        this.description = description;
    }

    /**
     * @param time    seconds of any clock
     * @param queries sets the entity's queries for this frame, anim_time and the controller queries are set here
     */
    public BedrockModel.Pose update(final double time, final Consumer<MutableObjectBinding> queries) {
        final double delta = Double.isNaN(this.lastTime) ? 0 : Math.max(0, Math.min(1, time - this.lastTime));
        this.lastTime = time;
        final MutableObjectBinding query = new MutableObjectBinding();
        queries.accept(query);
        query.set("delta_time", Value.of(delta));
        final Scope scope = BASE_SCOPE.copy();
        scope.set("query", query);
        scope.set("q", query);
        scope.set("variable", this.variables);
        scope.set("v", this.variables);
        scope.readOnly(true);

        final JsonObject scripts = BedrockPackIndex.object(this.description, "scripts");
        if (!this.initialized) {
            this.initialized = true;
            runScripts(scope, scripts != null ? scripts.get("initialize") : null);
        }
        runScripts(scope, scripts != null ? scripts.get("pre_animation") : null);

        final Map<String, float[]> pose = new HashMap<>();
        final List<String> active = new ArrayList<>();
        if (scripts != null && scripts.get("animate") instanceof final JsonArray entries) {
            for (final Entry entry : entries(entries)) {
                if (entry.weight() == null || entry.weight().evaluate(scope) != 0) {
                    active.add(entry.key());
                }
            }
        }
        // The older list of controllers, which always play
        if (this.description.get("animation_controllers") instanceof final JsonArray controllers) {
            for (final JsonElement controller : controllers) {
                if (controller instanceof final JsonObject named) {
                    for (final Map.Entry<String, JsonElement> entry : named.entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            active.add(entry.getValue().getAsString());
                        }
                    }
                } else if (controller.isJsonPrimitive()) {
                    active.add(controller.getAsString());
                }
            }
        }
        this.animate.keySet().retainAll(active);
        for (final String key : active) {
            Player player = this.animate.get(key);
            if (player == null && !this.animate.containsKey(key)) {
                player = this.player(key);
                this.animate.put(key, player);
            }
            if (player != null) {
                player.update(scope, query, delta, 1, pose);
            }
        }
        return new BedrockModel.Pose(pose);
    }

    /**
     * @return what plays an animation or controller by its short name in the entity's description or its identifier
     */
    private @Nullable Player player(final String key) {
        final JsonObject animations = BedrockPackIndex.object(this.description, "animations");
        final String identifier = animations != null && animations.get(key) instanceof final JsonElement value && value.isJsonPrimitive() ? value.getAsString() : key;
        final JsonObject controller = this.index.animationController(identifier);
        if (controller != null) {
            return new ControllerPlayer(controller);
        }
        final BedrockAnimation animation = this.index.parsedAnimation(identifier);
        return animation != null ? new AnimationPlayer(animation) : null;
    }

    private static void runScripts(final Scope scope, final @Nullable JsonElement scripts) {
        if (scripts instanceof final JsonArray list) {
            for (final JsonElement script : list) {
                if (script.isJsonPrimitive()) {
                    BedrockMoLang.run(scope, script.getAsString());
                }
            }
        } else if (scripts != null && scripts.isJsonPrimitive()) {
            BedrockMoLang.run(scope, scripts.getAsString());
        }
    }

    /**
     * @param weight a condition in animate lists, a blend weight in controller states, or null
     */
    private record Entry(String key, BedrockMoLang.@Nullable Value weight) {
    }

    private static List<Entry> entries(final JsonArray list) {
        final List<Entry> entries = new ArrayList<>();
        for (final JsonElement element : list) {
            if (element.isJsonPrimitive()) {
                entries.add(new Entry(element.getAsString(), null));
            } else if (element instanceof final JsonObject object) {
                for (final Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    entries.add(new Entry(entry.getKey(), BedrockMoLang.Value.of(entry.getValue())));
                }
            }
        }
        return entries;
    }

    private interface Player {

        void update(Scope scope, MutableObjectBinding query, double delta, double weight, Map<String, float[]> pose);

        /**
         * @return whether it played through at least once
         */
        boolean finished();

    }

    private static final class AnimationPlayer implements Player {

        private final BedrockAnimation animation;
        private double animTime;
        private double delay = -1;
        private boolean playedThrough;

        AnimationPlayer(final BedrockAnimation animation) {
            this.animation = animation;
        }

        @Override
        public void update(final Scope scope, final MutableObjectBinding query, final double delta, final double weight, final Map<String, float[]> pose) {
            if (this.delay < 0) {
                this.delay = this.animation.startDelay() != null ? this.animation.startDelay().evaluate(scope) : 0;
            }
            if (this.delay > 0) {
                this.delay -= delta;
                return;
            }
            if (this.animation.animTimeUpdate() != null) {
                query.set("anim_time", Value.of(this.animTime));
                this.animTime = BedrockMoLang.number(scope, this.animation.animTimeUpdate());
            } else {
                this.animTime += delta;
            }

            final double length = this.animation.length();
            double time = this.animTime;
            if (length > 0 && time >= length) {
                this.playedThrough = true;
                switch (this.animation.loop()) {
                    case LOOP -> time %= length;
                    case HOLD_ON_LAST_FRAME -> time = length;
                    case ONCE -> {
                        return;
                    }
                }
            }
            if (weight == 0) {
                return;
            }
            query.set("anim_time", Value.of(time));
            for (final Map.Entry<String, BedrockAnimation.Bone> bone : this.animation.bones().entrySet()) {
                final float[] values = pose.computeIfAbsent(bone.getKey(), k -> new float[]{0, 0, 0, 0, 0, 0, 1, 1, 1});
                final BedrockAnimation.Bone channels = bone.getValue();
                if (channels.rotation() != null) {
                    final double[] rotation = channels.rotation().evaluate(scope, time);
                    for (int i = 0; i < 3; i++) {
                        values[i] += (float) (rotation[i] * weight);
                    }
                }
                if (channels.position() != null) {
                    final double[] position = channels.position().evaluate(scope, time);
                    for (int i = 0; i < 3; i++) {
                        values[3 + i] += (float) (position[i] * weight);
                    }
                }
                if (channels.scale() != null) {
                    final double[] scale = channels.scale().evaluate(scope, time);
                    for (int i = 0; i < 3; i++) {
                        values[6 + i] *= (float) (1 + (scale[i] - 1) * weight);
                    }
                }
            }
        }

        @Override
        public boolean finished() {
            return this.playedThrough || this.animation.length() <= 0;
        }

    }

    private final class ControllerPlayer implements Player {

        private final JsonObject states;
        private String state;
        private final Map<String, Player> players = new HashMap<>();
        private boolean entered;

        ControllerPlayer(final JsonObject controller) {
            this.states = BedrockPackIndex.object(controller, "states") != null ? BedrockPackIndex.object(controller, "states") : new JsonObject();
            this.state = controller.get("initial_state") instanceof final JsonElement initial && initial.isJsonPrimitive() ? initial.getAsString() : "default";
            if (!this.states.has(this.state) && !this.states.isEmpty()) {
                this.state = this.states.keySet().iterator().next();
            }
        }

        @Override
        public void update(final Scope scope, final MutableObjectBinding query, final double delta, final double weight, final Map<String, float[]> pose) {
            JsonObject current = BedrockPackIndex.object(this.states, this.state);
            if (current == null) {
                return;
            }
            if (!this.entered) {
                this.entered = true;
                runScripts(scope, current.get("on_entry"));
            }
            // Transitions see whether the animations of the state played through
            query.set("all_animations_finished", Value.of(this.players.values().stream().allMatch(player -> player == null || player.finished())));
            query.set("any_animation_finished", Value.of(this.players.values().stream().anyMatch(player -> player != null && player.finished())));
            if (current.get("transitions") instanceof final JsonArray transitions) {
                for (final Entry transition : entries(transitions)) {
                    if (transition.weight() != null && transition.weight().evaluate(scope) != 0 && this.states.has(transition.key())) {
                        runScripts(scope, current.get("on_exit"));
                        this.state = transition.key();
                        this.players.clear();
                        current = BedrockPackIndex.object(this.states, this.state);
                        runScripts(scope, current != null ? current.get("on_entry") : null);
                        break;
                    }
                }
            }
            if (current == null || !(current.get("animations") instanceof final JsonArray animations)) {
                return;
            }
            for (final Entry entry : entries(animations)) {
                Player player = this.players.get(entry.key());
                if (player == null && !this.players.containsKey(entry.key())) {
                    player = BedrockAnimator.this.player(entry.key());
                    this.players.put(entry.key(), player);
                }
                if (player != null) {
                    final double blend = entry.weight() != null ? entry.weight().evaluate(scope) : 1;
                    player.update(scope, query, delta, weight * blend, pose);
                }
            }
        }

        @Override
        public boolean finished() {
            return false;
        }

    }

}
