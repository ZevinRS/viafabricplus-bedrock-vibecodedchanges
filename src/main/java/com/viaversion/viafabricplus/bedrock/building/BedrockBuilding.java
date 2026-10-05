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

import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import org.jetbrains.annotations.Nullable;

/**
 * Bedrock's hold-to-build: while the use button is held with a block, Bedrock keeps placing blocks every frame instead
 * of repeating the click every 4 ticks like Java. Ported from Bedrock's GameMode (continueBuildBlockAction,
 * continueBuildBlock, buildBlock), and checked against recordings of the Bedrock client:
 * <ul>
 *     <li>Without a build direction, blocks are placed against the targeted block face, or next to the last built
 *     block in the movement direction while moving.</li>
 *     <li>Two placements next to each other lock a build direction. From then on, the next block of the line is placed
 *     whenever the look ray (up to the block it hits, or the pick range) passes through it.</li>
 *     <li>Placements are limited by a delay depending on the player's speed.</li>
 * </ul>
 */
public final class BedrockBuilding {

    public static final double SURVIVAL_PICK_RANGE = 5.7; // With mouse input
    public static final double CREATIVE_PICK_RANGE = 12.0;

    private static final double MIN_MOVE_DELTA_SQR = 0.01;
    private static final long MAX_LAG_NANOS = 180_000_000L;
    private static final float MIN_MOVING_NON_CREATIVE_BUILD_DELAY = 100.0F;

    // Logs every building decision, for comparing against recordings of the Bedrock client
    private static final boolean DEBUG = Boolean.getBoolean("viafabricplus.bedrock.debugBuilding");

    private static final BedrockBuilding INSTANCE = new BedrockBuilding();

    private boolean hasBuildDirection;
    private boolean hasLastBuiltPosition;
    private BlockPos lastBuiltPosition = BlockPos.ZERO;
    private Vec3i buildDirection = Vec3i.ZERO;
    private BlockPos nextBuildPosition = BlockPos.ZERO;
    private Direction continueFacing = Direction.NORTH;
    private long lastBuildTime;
    private String branch = "";

    private BedrockBuilding() {
    }

    public static BedrockBuilding instance() {
        return INSTANCE;
    }

    public static boolean isActive() {
        return BedrockProtocolVersion.BEDROCK_LATEST.equals(ViaFabricPlus.api().targetVersion());
    }

    public static boolean isHoldingBlock(final LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof BlockItem;
    }

    /**
     * Bedrock's startBuildBlock: a new click on a block resets the build state.
     */
    public void startBuild() {
        this.hasBuildDirection = false;
        this.hasLastBuiltPosition = false;
    }

    public void stopBuild() {
        this.hasBuildDirection = false;
        this.hasLastBuiltPosition = false;
    }

    /**
     * Called when the click itself placed a block, which Bedrock remembers as the last built position.
     */
    public void onBlockPlaced(final BlockPos pos) {
        if (DEBUG) {
            ViaFabricPlusBedrock.impl().logger().info("[build] click placed {}", pos.toShortString());
        }
        this.lastBuiltPosition = pos;
        this.hasLastBuiltPosition = true;
        this.lastBuildTime = System.nanoTime();
    }

    /**
     * @return where a block placed against the hit would end up, matching Java's placement rules
     */
    public static @Nullable BlockPos placementPosition(final LocalPlayer player, final InteractionHand hand, final BlockHitResult hit) {
        if (!(player.getItemInHand(hand).getItem() instanceof BlockItem)) {
            return null;
        }
        return new BlockPlaceContext(new UseOnContext(player, hand, hit)).getClickedPos();
    }

    /**
     * Bedrock's continueBuildBlockAction, run every frame while the use button is held.
     */
    public void frame(final Minecraft minecraft, final float partialTicks) {
        final LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.gameMode == null) {
            return;
        }
        if (!minecraft.options.keyUse.isDown() || minecraft.gui.screen() != null || player.isUsingItem() || player.isHandsBusy()) {
            this.stopBuild();
            return;
        }
        if (!isHoldingBlock(player) || player.isSpectator()) {
            return;
        }

        final Vec3 eye = player.getEyePosition(partialTicks);
        final Vec3 rayEnd = eye.add(player.getViewVector(partialTicks).scale(player.blockInteractionRange()));
        final BlockHitResult hit = minecraft.level.clip(new ClipContext(eye, rayEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        final boolean hitBlock = hit.getType() == HitResult.Type.BLOCK;

        if (this.hasBuildDirection) {
            final Vec3 segmentEnd = hitBlock ? hit.getLocation() : rayEnd;
            if (intersects(new AABB(this.nextBuildPosition), eye, segmentEnd)) {
                this.branch = "line";
                this.continueBuild(minecraft, player, this.nextBuildPosition.subtract(this.buildDirection), this.continueFacing);
            }
            return;
        }
        if (!hitBlock) {
            return;
        }

        final Vec3 posDelta = posDelta(player);
        if (this.hasLastBuiltPosition && posDelta.lengthSqr() > MIN_MOVE_DELTA_SQR && !player.isShiftKeyDown()) {
            this.branch = "move";
            this.continueBuild(minecraft, player, this.lastBuiltPosition, Direction.getApproximateNearest(posDelta));
        } else {
            this.branch = "aim";
            this.continueBuild(minecraft, player, hit.getBlockPos(), hit.getDirection());
        }
    }

    /**
     * Bedrock's continueBuildBlock: applies the build delay, places the block and updates the build direction.
     */
    private void continueBuild(final Minecraft minecraft, final LocalPlayer player, final BlockPos pos, final Direction face) {
        final BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5)), face, pos, false);
        final BlockPos placePos = placementPosition(player, InteractionHand.MAIN_HAND, hit);
        if (placePos == null) {
            return;
        }

        final boolean sneaking = player.isShiftKeyDown();
        final double speed = posDelta(player).length() * 20.0;
        float delay = 300.0F;
        if (!sneaking && this.hasBuildDirection) { // Every held block counts as snappable
            delay = speed > 0.0 ? (float) Math.min(900.0 / speed, 180.0) : 200.0F;
        }
        if (!player.hasInfiniteMaterials() && delay < 100.0F) {
            delay = MIN_MOVING_NON_CREATIVE_BUILD_DELAY;
        }

        final long now = System.nanoTime();
        final long scheduled = this.lastBuildTime + (long) (int) delay * 1_000_000L;
        final long newLastBuildTime = speed > 0.0 ? Math.max(scheduled, now - MAX_LAG_NANOS) : now;
        if (now <= scheduled) {
            return;
        }

        final boolean built = this.place(minecraft, player, hit);
        if (DEBUG) {
            final Vec3 delta = posDelta(player);
            ViaFabricPlusBedrock.impl().logger().info("[build] {} {} {} -> {} built={} delay={} delta=({}, {}, {}) dir={} pitch={}",
                this.branch, pos.toShortString(), face, placePos.toShortString(), built, (int) delay,
                String.format("%.2f", delta.x), String.format("%.2f", delta.y), String.format("%.2f", delta.z),
                this.hasBuildDirection ? this.buildDirection.toShortString() : "-", String.format("%.1f", player.getXRot()));
        }
        if (!sneaking) {
            if (!this.hasBuildDirection) {
                if (this.hasLastBuiltPosition) {
                    final Vec3i delta = placePos.subtract(this.lastBuiltPosition);
                    if (Math.abs(delta.getX()) + Math.abs(delta.getY()) + Math.abs(delta.getZ()) == 1) {
                        this.hasBuildDirection = true;
                        this.nextBuildPosition = placePos;
                        this.buildDirection = delta;
                        this.continueFacing = Direction.getApproximateNearest(delta.getX(), delta.getY(), delta.getZ());
                    }
                }
            } else if (!this.hasLastBuiltPosition) {
                this.hasBuildDirection = false;
            }
            if (this.hasBuildDirection && placePos.equals(this.nextBuildPosition)) {
                if (!built) {
                    return;
                }
                this.nextBuildPosition = this.nextBuildPosition.offset(this.buildDirection);
            } else if (!built) {
                return;
            }
        } else if (!built) {
            return;
        }

        this.lastBuiltPosition = placePos;
        this.hasLastBuiltPosition = true;
        this.lastBuildTime = newLastBuildTime;
    }

    private boolean place(final Minecraft minecraft, final LocalPlayer player, final BlockHitResult hit) {
        final ItemStack held = player.getMainHandItem();
        final int oldCount = held.getCount();
        final InteractionResult result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        if (!(result instanceof final InteractionResult.Success success)) {
            return false;
        }
        if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            player.swing(InteractionHand.MAIN_HAND, held.getInteractAnimation(), false);
            if (!held.isEmpty() && (held.getCount() != oldCount || player.hasInfiniteMaterials())) {
                player.itemUsed(InteractionHand.MAIN_HAND);
            }
        }
        return true;
    }

    private static Vec3 posDelta(final LocalPlayer player) {
        return new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
    }

    private static boolean intersects(final AABB box, final Vec3 from, final Vec3 to) {
        if (box.contains(from)) {
            return true;
        }
        final Optional<Vec3> clip = box.clip(from, to);
        return clip.isPresent();
    }

}
