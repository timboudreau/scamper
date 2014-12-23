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

import com.mastfrog.util.Checks;
import java.util.Objects;

/**
 * A message to send, comprising the message type and a payload which can be
 * encoded by the bound Codec (by default BSON via Jackson), unless the payload
 * type is Netty's <code>ByteBuf</code> in which case the raw bytes will be
 * passed.
 * <p>
 * A Message must be associated with an existing message type, so the
 * constructor is private - create instances by using
 * <code>MessageType.newMessage(payload)</code>
 *
 * @author Tim Boudreau
 */
public final class Message<T> {

    /**
     * The message type. Will not be null.
     */
    public final MessageType type;
    /**
     * The message payload. May be null in the case that no payload is needed.
     */
    public final T body;

    Message(MessageType type, T obj) {
        Checks.notNull("type", type);
        this.body = obj;
        this.type = type;
    }

    public String toString() {
        return type + " " + body;
    }

    @Override
    public int hashCode() {
        return this.type.hashCode() * 41 + Objects.hashCode(this.body);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof Message) {
            Message other = (Message) obj;
            return type.equals(other.type) && Objects.equals(body, other.body);
        } else {
            return false;
        }
    }

}
