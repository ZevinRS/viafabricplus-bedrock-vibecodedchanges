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

package com.viaversion.viafabricplus.bedrock.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A block with a shape of its own a server defined, whose collision and outline come from the server.
 */
public final class BedrockShapedBlock extends Block implements BedrockCustomBlock {

    private final int index;

    public BedrockShapedBlock(final Properties properties, final int index) {
        super(properties);
        this.index = index;
    }

    @Override
    public @Nullable BedrockCustomBlockDefinition definition() {
        return BedrockCustomBlocks.shapedDefinition(this.index);
    }

    @Override
    protected VoxelShape getShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        final BedrockCustomBlockDefinition definition = this.definition();
        return definition != null ? definition.outline() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(final BlockState state, final BlockGetter level, final BlockPos pos, final CollisionContext context) {
        final BedrockCustomBlockDefinition definition = this.definition();
        return definition != null ? definition.collision() : Shapes.block();
    }

    @Override
    public float getFriction() {
        final BedrockCustomBlockDefinition definition = this.definition();
        return definition != null ? definition.friction() : super.getFriction();
    }

}
