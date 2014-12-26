/*
 * Copyright (c) 2014 Tim Boudreau
 *
 * This file is part of Scamper.
 *
 * Scamper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.mastfrog.scamper;

import com.google.common.collect.ImmutableSet;
import io.netty.buffer.ByteBuf;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public final class MessageTypeRegistry {

    private final Set<MessageType> types;

    MessageTypeRegistry(Set<MessageType> types) {
        this.types = ImmutableSet.copyOf(types);
    }

    void register(MessageType type) {
        types.add(type);
    }

    /**
     * Decode a message type from the <i>current position</i> of the
     * passed ByteBuf.
     * @param buf
     * @return 
     */
    public MessageType forByteBuf(ByteBuf buf) {
        if (buf.readableBytes() >= 2) {
            byte one = buf.readByte();
            byte two = buf.readByte();
            for (MessageType mt : types) {
                if (mt.match(one, two)) {
                    buf.discardReadBytes();
                    return mt;
                }
            }
            return MessageType.createUnknown(one, two);
        }
        buf.resetReaderIndex();
        return MessageType.createUnknown((byte) 0, (byte) 0);
    }
}
