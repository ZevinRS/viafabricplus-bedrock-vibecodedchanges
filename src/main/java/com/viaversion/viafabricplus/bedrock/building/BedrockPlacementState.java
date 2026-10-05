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

import com.viaversion.viaversion.api.connection.StorableObject;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import java.util.LinkedHashMap;
import java.util.Map;
import net.raphimc.viabedrock.protocol.model.BedrockItem;

/**
 * Per connection state for translating block placements the way the Bedrock client sends them. Only used on the netty
 * thread.
 */
public final class BedrockPlacementState implements StorableObject {

    // Bedrock counts legacy inventory request ids down in steps of 2, starting below -1
    private int nextLegacyRequestId = -2;

    private boolean usingItemOn;
    private BlockPosition lastPlacedPosition = new BlockPosition(0, 0, 0);
    private long placementSwingUntil;
    private long skipItemUseUntil;

    // The Bedrock client only sends mob_equipment when its held items change
    private int equippedSlot = -1;
    private BedrockItem equippedItem;
    private BedrockItem equippedOffhandItem;

    // Blocks the client placed, until the server updates them. Bedrock sends the clicked block as the client sees it.
    private final Map<BlockPosition, Integer> predictedBlocks = new LinkedHashMap<>(16, 0.75F, false) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<BlockPosition, Integer> eldest) {
            return this.size() > 256;
        }
    };

    public int nextLegacyRequestId() {
        final int id = this.nextLegacyRequestId;
        this.nextLegacyRequestId -= 2;
        return id;
    }

    public boolean usingItemOn() {
        return this.usingItemOn;
    }

    public void setUsingItemOn(final boolean usingItemOn) {
        this.usingItemOn = usingItemOn;
    }

    public BlockPosition lastPlacedPosition() {
        return this.lastPlacedPosition;
    }

    public void setLastPlacedPosition(final BlockPosition lastPlacedPosition) {
        this.lastPlacedPosition = lastPlacedPosition;
    }

    /**
     * The swing following a placement was already sent before the placement, like the Bedrock client does.
     */
    public void expectPlacementSwing() {
        this.placementSwingUntil = System.nanoTime() + 50_000_000L;
    }

    /**
     * The use of the item after using it on a block was already sent with it.
     */
    public void skipNextItemUse() {
        this.skipItemUseUntil = System.nanoTime() + 50_000_000L;
    }

    public boolean consumeSkippedItemUse() {
        final boolean skip = System.nanoTime() < this.skipItemUseUntil;
        this.skipItemUseUntil = 0;
        return skip;
    }

    public boolean consumePlacementSwing() {
        final boolean expected = System.nanoTime() < this.placementSwingUntil;
        this.placementSwingUntil = 0;
        return expected;
    }

    /**
     * @return whether the held item differs from the last one sent, and remembers it as sent if so
     */
    public boolean equip(final int slot, final BedrockItem item) {
        if (slot == this.equippedSlot && same(item, this.equippedItem)) {
            return false;
        }
        this.equippedSlot = slot;
        this.equippedItem = item.copy();
        return true;
    }

    /**
     * @return the held item as the client predicts it, which is only updated by the server once it handled placements
     */
    public BedrockItem heldItem(final int slot, final BedrockItem serverItem) {
        if (slot == this.equippedSlot && this.equippedItem != null && !this.equippedItem.isDifferent(serverItem)) {
            return this.equippedItem.copy();
        }
        return serverItem;
    }

    public boolean equipOffhand(final BedrockItem item) {
        if (same(item, this.equippedOffhandItem)) {
            return false;
        }
        this.equippedOffhandItem = item.copy();
        return true;
    }

    private static boolean same(final BedrockItem item, final BedrockItem other) {
        return other != null && !item.isDifferent(other) && item.amount() == other.amount();
    }

    public void predictBlock(final BlockPosition position, final int blockState) {
        this.predictedBlocks.put(position, blockState);
    }

    public void onServerBlockChange(final BlockPosition position) {
        this.predictedBlocks.remove(position);
    }

    public int blockState(final BlockPosition position, final int serverBlockState) {
        return this.predictedBlocks.getOrDefault(position, serverBlockState);
    }

}
