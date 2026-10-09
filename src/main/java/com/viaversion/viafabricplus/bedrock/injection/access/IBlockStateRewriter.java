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

package com.viaversion.viafabricplus.bedrock.injection.access;

import com.google.common.collect.BiMap;
import com.viaversion.viaversion.libs.fastutil.ints.Int2IntMap;
import java.util.List;
import net.raphimc.viabedrock.api.model.BlockState;
import net.raphimc.viabedrock.protocol.model.BlockProperties;

public interface IBlockStateRewriter {

    /**
     * @return the names of the blocks the server defined, in the order the Bedrock client numbers them
     */
    List<String> viaFabricPlusBedrock$getCustomBlocks();

    BlockProperties[] viaFabricPlusBedrock$getBlockProperties();

    /**
     * @return ViaBedrock's Bedrock block states and their runtime ids
     */
    BiMap<BlockState, Integer> viaFabricPlusBedrock$getBedrockStates();

    /**
     * @return ViaBedrock's Java block state of every Bedrock runtime id
     */
    Int2IntMap viaFabricPlusBedrock$getJavaStates();

}
