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

import com.fasterxml.jackson.annotation.JsonValue;
import io.netty.buffer.ByteBuf;

/**
 * Message types. Our SCTP messages begin with a 3-byte signature, consisting of
 * a magic number and two bytes identifying the type of message, which is used
 * to look up the handler the message will be dispatched to.
 * <p>
 * When creating a server, create some MessageType instances, and then use
 * ProtocolModule.bind() to map those to handlers.
 *
 * @author Tim Boudreau
 */
public final class MessageType {

    private final String name;
    private final byte byteOne;
    private final byte byteTwo;
    private boolean unknown;
    public static final int HEADER_SIZE = 2;

    public MessageType(String name, int byteOne, int byteTwo) {
        this(name, byteOne, byteTwo, false);
    }

    public MessageType(String name, byte byteOne, byte byteTwo) {
        this(name, byteOne, byteTwo, false);
    }

    private MessageType(String name, int byteOne, int byteTwo, boolean unknown) {
        this(name, (byte) byteOne, (byte) byteTwo, unknown);
        if (byteOne > Byte.MAX_VALUE || byteOne < Byte.MIN_VALUE) {
            throw new IllegalArgumentException("byteOne not expressible as a byte ("
                    + Byte.MIN_VALUE + "-" + Byte.MAX_VALUE + "): " + byteOne);
        }
        if (byteTwo > Byte.MAX_VALUE || byteTwo < Byte.MIN_VALUE) {
            throw new IllegalArgumentException("byteTwo not expressible as a byte ("
                    + Byte.MIN_VALUE + "-" + Byte.MAX_VALUE + "): " + byteTwo);
        }
    }

    private MessageType(String name, byte byteOne, byte byteTwo, boolean unknown) {
        if (!unknown && byteOne == 0 && byteTwo == 0) {
            throw new IllegalArgumentException("Zero/zero is reserved "
                    + "for unknown messages");
        }
        this.name = name;
        this.byteOne = byteOne;
        this.byteTwo = byteTwo;
        this.unknown = unknown;
    }

    public <T> Message<T> newMessage(T obj) {
        return new Message<>(this, obj);
    }

    public int headerLength() {
        return HEADER_SIZE;
    }

    boolean match(byte one, byte two) {
        return byteOne == one && byteTwo == two;
    }

    /**
     * Returns true if this MessageType is not a registered one - it contains a
     * byte sequence the application doesn't recognize.
     *
     * @return true if this message is unknown
     */
    public boolean isUnknown() {
        return unknown;
    }

    private String stringValue;
    @JsonValue
    public String toString() {
        if (stringValue == null) {
            stringValue = "0x" + hexString(byteOne) + hexString(byteTwo)
                    + "(" + name + ")";
        }
        return stringValue;
    }

    private static String hexString(int val) {
        String result = Integer.toHexString(val);
        if (result.length() == 1) {
            result = "0" + result;
        }
        return result;
    }

    /**
     * Write this mesage type into a ByteBuf
     *
     * @param buf The buffeer
     * @return the buffer
     */
    public ByteBuf writeHeader(ByteBuf buf) {
        return buf.writeByte(byteOne).writeByte(byteTwo);
    }

    public static MessageType createUnknown(int one, int two) {
        if (one > Byte.MAX_VALUE || one < Byte.MIN_VALUE) {
            throw new IllegalArgumentException("Illegal byte value " + one);
        }
        if (two > Byte.MAX_VALUE || two < Byte.MIN_VALUE) {
            throw new IllegalArgumentException("Illegal byte value " + two);
        }
        return createUnknown((byte) one, (byte) two);
    }

    public static MessageType createUnknown(byte one, byte two) {
        return new MessageType("UNKNOWN", one, two, true);
    }

    @Override
    public int hashCode() {
        return ((byteOne & 0xFF) << 8) | (byteTwo & 0xFF);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MessageType) {
            MessageType other = (MessageType) obj;
            return other.byteOne == byteOne && other.byteTwo == byteTwo;
        }
        return false;
    }
}
