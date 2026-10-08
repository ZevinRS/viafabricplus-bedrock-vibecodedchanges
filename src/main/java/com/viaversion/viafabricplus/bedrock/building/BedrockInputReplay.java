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

package com.viaversion.viafabricplus.bedrock.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viaversion.api.connection.UserConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;

/**
 * Debug tool for comparing movement with the Bedrock client: plays back the per tick input of a recorded Bedrock run
 * (keys and rotation), starting from the same position. Enabled with -Dviafabricplus.bedrock.inputReplay=path/to/replay.json,
 * started with F7. The file holds the start position and one frame per tick: yaw, pitch and the held keys as letters
 * (F forward, B backward, L left, R right, J jump, S sneak, P sprint). A replay that starts in the middle of a run also
 * holds the velocity and sprinting to start with. Servers without a tp command can teleport
 * through chat with -Dviafabricplus.bedrock.inputReplayTeleport=chat, which sends "!tp x y z".
 * With -Dviafabricplus.bedrock.inputReplayAuto=host:port the client joins that server from the title screen, plays the
 * replay once the world loaded and closes the game afterwards, so runs need no one at the keyboard. With
 * -Dviafabricplus.bedrock.inputReplayChat=frame:message the client sends a chat message before that frame, like a test
 * server command. With -Dviafabricplus.bedrock.inputReplayStopAfterSplit=ticks the replay ends that many ticks after the player first got
 * further than 0.05 from the recorded Bedrock position. A replay can list the velocities the server set for the Bedrock
 * player with the frame it first used each in ("motions"); the client then uses those in the same frames and ignores the
 * ones the server sets for it, so network timing can't make the runs differ.
 */
public final class BedrockInputReplay {

    private static final String FILE = System.getProperty("viafabricplus.bedrock.inputReplay");
    private static final boolean TELEPORT_THROUGH_CHAT = "chat".equals(System.getProperty("viafabricplus.bedrock.inputReplayTeleport"));
    private static final int TELEPORT_WAIT_TICKS = 40;
    private static final String AUTO_SERVER = System.getProperty("viafabricplus.bedrock.inputReplayAuto");
    private static final int AUTO_START_TICKS = 100;
    private static final int AUTO_QUIT_TICKS = 40;
    private static final int STOP_AFTER_SPLIT = Integer.getInteger("viafabricplus.bedrock.inputReplayStopAfterSplit", -1);
    private static final String CHAT = System.getProperty("viafabricplus.bedrock.inputReplayChat");
    private static final double SPLIT_DISTANCE = 0.05;
    // The client player's eye height, which the recorded Bedrock positions are at
    private static final double EYE_HEIGHT = 1.62;

    private static boolean autoConnected;
    private static int autoTicks;

    private static State state = State.IDLE;
    private static boolean keyWasDown;
    private static int waitTicks;
    private static int frameIndex;
    private static double[] start;
    // Velocity and sprinting of the Bedrock player when the replay starts in the middle of a run, or null
    private static double[] startVelocity;
    private static boolean startSprinting;
    private static final List<Frame> FRAMES = new ArrayList<>();
    private static final List<double[]> POSITIONS = new ArrayList<>();
    // Frame and velocity of every velocity the server set for the Bedrock player
    private static final Deque<double[]> MOTIONS = new ArrayDeque<>();
    private static boolean recordedMotion;
    private static int splitFrame;

    private BedrockInputReplay() {
    }

    public static boolean isPlaying() {
        return state == State.PLAYING;
    }

    public static int frameIndex() {
        return frameIndex;
    }

    /**
     * @return whether the velocities the server sets for the player are replaced with the recorded ones
     */
    public static boolean usesRecordedMotion() {
        return state == State.PLAYING && recordedMotion;
    }

    /**
     * Called at the end of every client tick.
     *
     * @return the recorded velocity the player uses from its next move on, or null
     */
    public static Vec3 pollRecordedMotion() {
        if (!usesRecordedMotion() || MOTIONS.isEmpty() || MOTIONS.peek()[0] > frameIndex) {
            return null;
        }
        final double[] motion = MOTIONS.poll();
        return new Vec3(motion[1], motion[2], motion[3]);
    }

    /**
     * Called at the end of every client tick.
     */
    public static void tick(final Minecraft minecraft) {
        if (FILE == null) {
            return;
        }
        if (AUTO_SERVER != null && !autoConnected && minecraft.gui.screen() instanceof TitleScreen screen) {
            autoConnected = true;
            ViaFabricPlus.api().setTargetVersion(BedrockProtocolVersion.BEDROCK_LATEST);
            final ServerData server = new ServerData("Replay", AUTO_SERVER, ServerData.Type.OTHER);
            // Nobody is there to answer the resource pack prompt
            server.setResourcePackStatus(ServerData.ServerPackStatus.DISABLED);
            ConnectScreen.startConnecting(screen, minecraft, ServerAddress.parseString(AUTO_SERVER), server, false, null);
        }
        if (minecraft.player == null) {
            return;
        }
        if (AUTO_SERVER != null) {
            // Counts up to the start while idle, then again from 0 once the replay is done
            if (state == State.IDLE && ++autoTicks == AUTO_START_TICKS) {
                begin(minecraft.player);
                autoTicks = 0;
            } else if (state == State.DONE && ++autoTicks == AUTO_QUIT_TICKS) {
                ViaFabricPlusBedrock.impl().logger().info("[replay] closing the game");
                minecraft.stop();
            }
        }
        final boolean keyDown = InputConstants.isKeyDown(InputConstants.KEY_F7);
        if (keyDown && !keyWasDown && minecraft.gui.screen() == null) {
            if (state == State.IDLE || state == State.DONE) {
                begin(minecraft.player);
            } else {
                ViaFabricPlusBedrock.impl().logger().info("[replay] stopped");
                state = State.IDLE;
            }
        }
        keyWasDown = keyDown;

        if (state == State.PLAYING && minecraft.player.isDeadOrDying()) {
            ViaFabricPlusBedrock.impl().logger().info("[replay] died at frame {}", frameIndex);
            state = State.DONE;
        }
        if (state == State.PLAYING && frameIndex > 0 && frameIndex <= POSITIONS.size()) {
            checkSplit(minecraft.player);
        }
        if (state == State.TELEPORTING && --waitTicks <= 0) {
            frameIndex = 0;
            splitFrame = -1;
            state = State.PLAYING;
            if (startVelocity != null) {
                minecraft.player.setDeltaMovement(startVelocity[0], startVelocity[1], startVelocity[2]);
                minecraft.player.setSprinting(startSprinting);
            }
            ViaFabricPlusBedrock.impl().logger().info("[replay] playing {} frames, first tick {}", FRAMES.size(), lastSentTick() + 1);
        }
    }

    private static void begin(final LocalPlayer player) {
        try {
            final JsonObject json = JsonParser.parseString(Files.readString(Path.of(FILE))).getAsJsonObject();
            final JsonObject startJson = json.getAsJsonObject("start");
            start = new double[]{startJson.get("x").getAsDouble(), startJson.get("y").getAsDouble(), startJson.get("z").getAsDouble(),
                startJson.get("yaw").getAsDouble(), startJson.get("pitch").getAsDouble()};
            startVelocity = null;
            if (startJson.has("velocity")) {
                final JsonArray velocity = startJson.getAsJsonArray("velocity");
                startVelocity = new double[]{velocity.get(0).getAsDouble(), velocity.get(1).getAsDouble(), velocity.get(2).getAsDouble()};
                startSprinting = startJson.has("sprinting") && startJson.get("sprinting").getAsBoolean();
            }
            FRAMES.clear();
            POSITIONS.clear();
            MOTIONS.clear();
            recordedMotion = json.has("motions");
            if (recordedMotion) {
                for (final JsonElement element : json.getAsJsonArray("motions")) {
                    final JsonArray motion = element.getAsJsonArray();
                    MOTIONS.add(new double[]{motion.get(0).getAsInt(), motion.get(1).getAsDouble(), motion.get(2).getAsDouble(), motion.get(3).getAsDouble()});
                }
            }
            if (json.has("positions")) {
                for (final JsonElement element : json.getAsJsonArray("positions")) {
                    final JsonArray position = element.getAsJsonArray();
                    POSITIONS.add(new double[]{position.get(0).getAsDouble(), position.get(1).getAsDouble(), position.get(2).getAsDouble()});
                }
            }
            for (final JsonElement element : json.getAsJsonArray("frames")) {
                final JsonArray frame = element.getAsJsonArray();
                final String keys = frame.get(2).getAsString();
                FRAMES.add(new Frame(frame.get(0).getAsFloat(), frame.get(1).getAsFloat(), new Input(keys.indexOf('F') >= 0, keys.indexOf('B') >= 0,
                    keys.indexOf('L') >= 0, keys.indexOf('R') >= 0, keys.indexOf('J') >= 0, keys.indexOf('S') >= 0, keys.indexOf('P') >= 0)));
            }
        } catch (final Exception e) {
            ViaFabricPlusBedrock.impl().logger().error("[replay] failed to read {}", FILE, e);
            return;
        }
        if (TELEPORT_THROUGH_CHAT) {
            player.connection.sendChat(String.format(Locale.ROOT, "!tp %.5f %.5f %.5f", start[0], start[1], start[2]));
        } else {
            player.connection.sendCommand(String.format(Locale.ROOT, "tp @s %.5f %.5f %.5f %.3f %.3f", start[0], start[1], start[2], start[3], start[4]));
        }
        waitTicks = TELEPORT_WAIT_TICKS;
        state = State.TELEPORTING;
        ViaFabricPlusBedrock.impl().logger().info("[replay] teleporting to the start of {} frames", FRAMES.size());
    }

    /**
     * Called after the client read its keys for the tick, before it moves.
     *
     * @return the move vector to use, or null when not playing
     */
    public static Vec2 applyFrame(final LocalPlayer player, final net.minecraft.client.player.ClientInput input) {
        if (state != State.PLAYING) {
            return null;
        }
        if (frameIndex >= FRAMES.size()) {
            state = State.DONE;
            ViaFabricPlusBedrock.impl().logger().info("[replay] done, last tick {}", lastSentTick());
            return null;
        }
        if (CHAT != null && CHAT.startsWith(frameIndex + ":")) {
            player.connection.sendChat(CHAT.substring(CHAT.indexOf(':') + 1));
            ViaFabricPlusBedrock.impl().logger().info("[replay] sent \"{}\" before frame {}", CHAT.substring(CHAT.indexOf(':') + 1), frameIndex);
        }
        final Frame frame = FRAMES.get(frameIndex++);
        player.setYRot(frame.yaw);
        player.setXRot(frame.pitch);
        player.setYHeadRot(frame.yaw);
        input.keyPresses = frame.keys;
        return new Vec2(impulse(frame.keys.left(), frame.keys.right()), impulse(frame.keys.forward(), frame.keys.backward())).normalized();
    }

    /**
     * Called after the player moved for a frame, compares its position with the one the Bedrock player had.
     */
    private static void checkSplit(final LocalPlayer player) {
        final int frame = frameIndex - 1;
        if (STOP_AFTER_SPLIT < 0) {
            return;
        }
        if (splitFrame < 0) {
            final double[] position = POSITIONS.get(frame);
            final double distance = Math.sqrt(Math.pow(player.getX() - position[0], 2) + Math.pow(player.getY() + EYE_HEIGHT - position[1], 2)
                + Math.pow(player.getZ() - position[2], 2));
            if (distance > SPLIT_DISTANCE) {
                splitFrame = frame;
                ViaFabricPlusBedrock.impl().logger().info("[replay] split at frame {}, off by {}", frame, distance);
            }
        } else if (frame >= splitFrame + STOP_AFTER_SPLIT) {
            ViaFabricPlusBedrock.impl().logger().info("[replay] stopped {} frames after the split", STOP_AFTER_SPLIT);
            state = State.DONE;
        }
    }

    private static float impulse(final boolean positive, final boolean negative) {
        return positive == negative ? 0F : positive ? 1F : -1F;
    }

    private static long lastSentTick() {
        final UserConnection user = ViaFabricPlus.api().userConnection();
        return user != null ? user.get(EntityTracker.class).getClientPlayer().age() : -1;
    }

    private enum State {
        IDLE, TELEPORTING, PLAYING, DONE
    }

    private record Frame(float yaw, float pitch, Input keys) {
    }

}
