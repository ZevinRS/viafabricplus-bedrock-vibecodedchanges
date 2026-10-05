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

package com.viaversion.viafabricplus.bedrock.inventory;

import com.viaversion.viaversion.api.connection.StorableObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.raphimc.viabedrock.api.model.container.Container;
import net.raphimc.viabedrock.protocol.model.BedrockItem;

/**
 * Item stack requests sent to the server and not answered yet. Only used on the netty thread.
 */
public final class BedrockInventoryState implements StorableObject {

    // Bedrock counts item stack request ids down in steps of 2, starting at -1
    private int nextRequestId = -1;

    private final Map<Integer, List<SlotSnapshot>> pendingRequests = new HashMap<>();
    // The request that last changed a slot, which is also the stack id the client uses for it until the server answers
    private final Map<SlotKey, Integer> lastRequests = new HashMap<>();
    // The item in a slot before the first request that is not answered yet changed it
    private final Map<SlotKey, BedrockItem> baseItems = new HashMap<>();

    public int nextRequestId() {
        final int id = this.nextRequestId;
        this.nextRequestId -= 2;
        return id;
    }

    public void addPendingRequest(final int requestId, final List<SlotSnapshot> snapshots) {
        this.pendingRequests.put(requestId, snapshots);
        for (final SlotSnapshot snapshot : snapshots) {
            this.lastRequests.put(snapshot.key(), requestId);
            this.baseItems.putIfAbsent(snapshot.key(), snapshot.item());
        }
    }

    public List<SlotSnapshot> removePendingRequest(final int requestId) {
        return this.pendingRequests.remove(requestId);
    }

    /**
     * @return whether the request was the last one to change the slot, so its answer decides the slot's item
     */
    public boolean isLastRequest(final SlotKey key, final int requestId) {
        final Integer last = this.lastRequests.get(key);
        return last != null && last == requestId;
    }

    /**
     * The slots a request was the last to change are now what the server says.
     */
    public void onRequestAccepted(final int requestId, final List<SlotSnapshot> snapshots) {
        for (final SlotSnapshot snapshot : snapshots) {
            if (this.isLastRequest(snapshot.key(), requestId)) {
                this.lastRequests.remove(snapshot.key());
                this.baseItems.remove(snapshot.key());
            }
        }
    }

    /**
     * @return the items to put back into the slots of a rejected request, from before any request changed them
     */
    public Map<SlotKey, BedrockItem> onRequestRejected(final List<SlotSnapshot> snapshots) {
        final Map<SlotKey, BedrockItem> restored = new HashMap<>();
        for (final SlotSnapshot snapshot : snapshots) {
            this.lastRequests.remove(snapshot.key());
            final BedrockItem base = this.baseItems.remove(snapshot.key());
            if (base != null) {
                restored.put(snapshot.key(), base);
            }
        }
        return restored;
    }

    public void clear() {
        this.pendingRequests.clear();
        this.lastRequests.clear();
        this.baseItems.clear();
    }

    /**
     * A slot of one of the tracked containers. Containers are compared by identity.
     */
    public record SlotKey(Container container, int index) {

        @Override
        public boolean equals(final Object o) {
            return o instanceof final SlotKey other && other.container == this.container && other.index == this.index;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.container) * 31 + this.index;
        }

    }

    /**
     * The item in a slot before a request changed it.
     */
    public record SlotSnapshot(SlotKey key, BedrockItem item) {
    }

}
