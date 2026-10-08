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

package com.viaversion.viafabricplus.bedrock.injection.mixin.features.movement;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.viaversion.viafabricplus.ViaFabricPlus;
import com.viaversion.viafabricplus.bedrock.ViaFabricPlusBedrock;
import com.viaversion.viafabricplus.bedrock.building.BedrockInputReplay;
import com.viaversion.viafabricplus.bedrock.building.BedrockSprint;
import com.viaversion.viaversion.api.connection.UserConnection;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viabedrock.protocol.data.enums.bedrock.generated.PlayerAuthInputData;
import net.raphimc.viabedrock.protocol.storage.EntityTracker;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntity {

    @Shadow
    protected Vec3 stuckSpeedMultiplier;

    @Shadow
    public abstract boolean isSwimming();

    /**
     * Bedrock starts swimming as soon as the block at the eyes holds water, not only once the eyes are below the water
     * surface a tick ago, and not while looking up (SwimTriggerSystem::doTick), as recorded when sinking into water.
     */
    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void bedrockSwimStart(final CallbackInfo ci) {
        final Entity entity = (Entity) (Object) this;
        if (this.isSwimming() || !ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return;
        }
        final boolean eyesInWater = entity.level().getFluidState(BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ())).is(FluidTags.WATER);
        entity.setSwimming(entity.isSprinting() && eyesInWater && entity.getLookAngle().y < 0.15 && !entity.isPassenger());
        ci.cancel();
    }

    /**
     * Bedrock takes the friction of the block below the player's center, not of the block it is supported by, as
     * recorded when walking off the edge of a slime block: Bedrock had normal friction once the center was past the
     * edge, while the slime still slowed the player down as long as it stood on it.
     */
    @Inject(method = "getBlockPosBelowThatAffectsMyMovement", at = @At("HEAD"), cancellable = true)
    private void frictionBelowCenter(final CallbackInfoReturnable<BlockPos> cir) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            final Entity entity = (Entity) (Object) this;
            cir.setReturnValue(BlockPos.containing(entity.getX(), entity.getY() - 0.500001, entity.getZ()));
        }
    }

    /**
     * Soul sand doesn't scale the velocity on Bedrock, it only slows the player down by its higher ground friction, see
     * {@link com.viaversion.viafabricplus.bedrock.injection.mixin.features.movement.MixinLivingEntity}.
     */
    @WrapOperation(method = "getBlockSpeedFactor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getSpeedFactor()F"))
    private float noSoulSandSpeedFactor(final Block block, final Operation<Float> original) {
        return block == Blocks.SOUL_SAND && ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) ? 1F : original.call(block);
    }

    /**
     * Java only finds the ground when the player moves down onto it, so a velocity without a downward part, like one the
     * server set, makes a player standing on the ground count as in the air for that move. Bedrock keeps it on the
     * ground, as recorded in a cobweb where the server kept setting the velocity. A tiny downward move finds the ground.
     */
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3 keepGroundContact(final Vec3 movement, final MoverType type) {
        final Entity entity = (Entity) (Object) this;
        if (type == MoverType.SELF && movement.y == 0 && entity.onGround() && entity == Minecraft.getInstance().player
            && ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return new Vec3(movement.x, -1.0E-7, movement.z);
        }
        return movement;
    }

    @Shadow
    @Final
    private LongSet visitedBlocks;

    @Shadow
    protected abstract boolean isAffectedByBlocks();

    @Shadow
    private int checkInsideBlocks(final Vec3 from, final Vec3 to, final InsideBlockEffectApplier.StepBasedCollector effectCollector, final LongSet visitedBlocks,
                                  final int maxMovementIterations) {
        throw new AssertionError();
    }

    /**
     * Bedrock finds the blocks the player is inside, like cobwebs, only at its position after the move, while Java goes
     * through the blocks along the whole move, as recorded when leaving a cobweb: Bedrock got free a tick before Java.
     */
    @Inject(method = "checkInsideBlocks(Ljava/util/List;Lnet/minecraft/world/entity/InsideBlockEffectApplier$StepBasedCollector;)V", at = @At("HEAD"), cancellable = true)
    private void insideBlocksAtPosition(final List<?> movements, final InsideBlockEffectApplier.StepBasedCollector effectCollector, final CallbackInfo ci) {
        final Entity entity = (Entity) (Object) this;
        if (entity == Minecraft.getInstance().player && ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            if (this.isAffectedByBlocks() && !movements.isEmpty()) {
                this.checkInsideBlocks(entity.position(), entity.position(), effectCollector, this.visitedBlocks, 16);
                this.visitedBlocks.clear();
            }
            ci.cancel();
        }
    }

    @Unique
    private Vec3 viaFabricPlusBedrock$moveFrom;

    @Inject(method = "move", at = @At("HEAD"))
    private void rememberMoveStart(final MoverType type, final Vec3 movement, final CallbackInfo ci) {
        if (type == MoverType.SELF) {
            this.viaFabricPlusBedrock$moveFrom = ((Entity) (Object) this).position();
        }
    }

    /**
     * Bedrock applies the effect of the block the player stands on, like the slowdown of slime, only while the block
     * is below the player's position before the move with a box reaching 0.2 from the center, like it does for water,
     * as recorded when walking off the edge of a slime block. Java uses the block supporting the player after the move.
     */
    @WrapOperation(method = "applyEffectsFromBlocks(Ljava/util/List;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getOnPosLegacy()Lnet/minecraft/core/BlockPos;"))
    private BlockPos bedrockSteppedOnBlock(final Entity instance, final Operation<BlockPos> original) {
        final BlockPos pos = original.call(instance);
        final Vec3 from = this.viaFabricPlusBedrock$moveFrom;
        if (from == null || !ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return pos;
        }
        final double halfWidth = 0.2;
        final boolean below = pos.getX() + 1 > from.x - halfWidth && pos.getX() < from.x + halfWidth
            && pos.getZ() + 1 > from.z - halfWidth && pos.getZ() < from.z + halfWidth;
        return below ? pos : BlockPos.containing(from.x, from.y - 0.2, from.z);
    }

    @Inject(method = "setSwimming", at = @At("HEAD"))
    private void trackSwimming(final boolean swimming, final CallbackInfo ci) {
        if (!ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return;
        }

        final UserConnection connection = ViaFabricPlus.api().userConnection();
        if (connection != null && swimming != this.isSwimming()) {
            connection.get(EntityTracker.class).getClientPlayer().addAuthInputData(swimming ? PlayerAuthInputData.StartSwimming : PlayerAuthInputData.StopSwimming);
            if (swimming && (Object) this == Minecraft.getInstance().player) {
                BedrockSprint.onSwimStart();
            }
        }
    }

    @Redirect(method = "makeStuckInBlock", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Entity;stuckSpeedMultiplier:Lnet/minecraft/world/phys/Vec3;", opcode = Opcodes.PUTFIELD))
    private void prioritySlowestMovementMultiplier(final Entity instance, final Vec3 value) {
        if (ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) && this.stuckSpeedMultiplier != Vec3.ZERO) {
            this.stuckSpeedMultiplier = new Vec3(Math.min(this.stuckSpeedMultiplier.x, value.x), Math.min(this.stuckSpeedMultiplier.y, value.y), Math.min(this.stuckSpeedMultiplier.z, value.z));
        } else {
            this.stuckSpeedMultiplier = value;
        }
    }

    /**
     * Java skips moves shorter than about 0.0003 that weren't stopped by a collision. Bedrock moves however little the
     * movement is, as recorded when the player slides to a stop.
     */
    @Definition(id = "movementLength", local = @Local(type = double.class, name = "movementLength"))
    @Expression("movementLength > ?")
    @ModifyExpressionValue(method = "move", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean moveAnyDistance(final boolean original, @Local(name = "movementLength") final double movementLength) {
        return original || movementLength > 0 && ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST);
    }

    // Bedrock ignores the box a vehicle would apply to its passengers
    @ModifyExpressionValue(method = "getFluidInteractionBox", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getVehicle()Lnet/minecraft/world/entity/Entity;"))
    private Entity skipPassengerChanges(final Entity vehicle) {
        return ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) ? null : vehicle;
    }

    /**
     * Bedrock inflates the box before deflating it, which is the same as inflating the deflated box. Horizontally it
     * reaches 0.2 from the player's center instead of 0.3, as recorded from where the water current changes.
     */
    @ModifyReturnValue(method = "getFluidInteractionBox", at = @At("RETURN"))
    private @Nullable AABB inflateFluidInteractionBox(final @Nullable AABB box) {
        if (box != null && ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST)) {
            return box.inflate(-0.099, -0.4, -0.099);
        } else {
            return box;
        }
    }

    /**
     * The mouse doesn't turn the player while an input replay sets the rotation of every tick.
     */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void keepReplayRotation(final double yaw, final double pitch, final CallbackInfo ci) {
        if (BedrockInputReplay.isPlaying() && (Object) this == Minecraft.getInstance().player) {
            ci.cancel();
        }
    }

    @Unique
    private Vec3 viaFabricPlusBedrock$replayCollided;

    @Inject(method = "collide", at = @At("RETURN"))
    private void rememberCollision(final Vec3 movement, final CallbackInfoReturnable<Vec3> cir) {
        if ((Object) this == Minecraft.getInstance().player) {
            this.viaFabricPlusBedrock$replayCollided = cir.getReturnValue();
        }
    }

    /**
     * Bedrock stops sprinting after a move only when the axis the player mostly tried to move along was blocked, see
     * {@link com.viaversion.viafabricplus.bedrock.injection.mixin.features.movement.MixinLocalPlayer}.
     */
    @Inject(method = "move", at = @At("TAIL"))
    private void rememberBlockedMainAxis(final MoverType type, final Vec3 movement, final CallbackInfo ci) {
        if (type != MoverType.SELF || (Object) this != Minecraft.getInstance().player || this.viaFabricPlusBedrock$replayCollided == null) {
            return;
        }
        final Vec3 collided = this.viaFabricPlusBedrock$replayCollided;
        final double epsilon = 5.0E-5;
        BedrockSprint.setMainAxisBlocked(Math.abs(movement.x) < Math.abs(movement.z) && Math.abs(collided.z) < epsilon
            || Math.abs(movement.z) < Math.abs(movement.x) && Math.abs(collided.x) < epsilon);
    }

    /**
     * Logs every movement of the player during an input replay, for comparing with the Bedrock client.
     */
    @Inject(method = "move", at = @At("TAIL"))
    private void logReplayMovement(final MoverType type, final Vec3 movement, final CallbackInfo ci) {
        final Entity entity = (Entity) (Object) this;
        if (!BedrockInputReplay.isPlaying() || entity != Minecraft.getInstance().player || type != MoverType.SELF) {
            return;
        }
        final Vec3 collided = this.viaFabricPlusBedrock$replayCollided;
        final Vec3 velocity = entity.getDeltaMovement();
        ViaFabricPlusBedrock.impl().logger().info(String.format(java.util.Locale.ROOT,
            "[replay-move] frame=%d intended=(%.5f,%.5f,%.5f) collided=(%.5f,%.5f,%.5f) velocity=(%.5f,%.5f,%.5f) onGround=%s horizontal=%s minor=%s vertical=%s speed=%.4f sprinting=%s swimming=%s inWater=%s underWater=%s feet=%s below=%s t=%d",
            BedrockInputReplay.frameIndex() - 1, movement.x, movement.y, movement.z,
            collided != null ? collided.x : Double.NaN, collided != null ? collided.y : Double.NaN, collided != null ? collided.z : Double.NaN,
            velocity.x, velocity.y, velocity.z, entity.onGround(), entity.horizontalCollision, entity.minorHorizontalCollision, entity.verticalCollision,
            entity instanceof final LivingEntity living ? living.getAttributeValue(Attributes.MOVEMENT_SPEED) : Double.NaN,
            entity.isSprinting(), entity.isSwimming(), entity.isInWater(), entity.isUnderWater(),
            BuiltInRegistries.BLOCK.getKey(entity.level().getBlockState(entity.blockPosition()).getBlock()).getPath(),
            BuiltInRegistries.BLOCK.getKey(entity.level().getBlockState(BlockPos.containing(entity.getX(), entity.getY() - 0.5, entity.getZ())).getBlock()).getPath(),
            System.currentTimeMillis()));
    }

    @Unique
    private static final ImmutableList<Direction.Axis> BEDROCK_AXIS_ORDER = ImmutableList.of(Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z);

    /**
     * Java collides along the larger horizontal axis first, Bedrock always along X before Z. Found with the input replay:
     * sliding forward past the end of a wall freed the sideways movement on Java a tick before Bedrock.
     */
    @WrapOperation(method = "collideWithShapes", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Direction;axisStepOrder(Lnet/minecraft/world/phys/Vec3;)Lcom/google/common/collect/ImmutableList;"))
    private static ImmutableList<Direction.Axis> bedrockAxisOrder(final Vec3 movement, final Operation<ImmutableList<Direction.Axis>> original) {
        return ViaFabricPlus.api().targetVersion().equals(BedrockProtocolVersion.BEDROCK_LATEST) ? BEDROCK_AXIS_ORDER : original.call(movement);
    }

}
