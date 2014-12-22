package com.mastfrog.scamper.protocol;

import static com.mastfrog.scamper.protocol.MessageTypeRegistry.MAGIC;
import io.netty.buffer.ByteBuf;

/**
 * Message types.  Our SCTP messages begin with a 3-byte signature, consisting
 * of a magic number and two bytes identifying the type of message, which is
 * used to look up the handler the message will be dispatched to.
 * <p>
 * When creating a server, create some MessageType instances, and then use
 * ProtocolModule.bind() to map those to handlers.
 *
 * @author Tim Boudreau
 */
public class MessageType {

    private final String name;
    private final byte byteOne;
    private final byte byteTwo;
    private boolean unknown;

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

    boolean match(byte one, byte two) {
        return byteOne == one && byteTwo == two;
    }

    public boolean isUnknown() {
        return unknown;
    }

    public String toString() {
        return name + " (" + byteOne + "," + byteTwo + ")";
    }

    public ByteBuf writeHeader(ByteBuf buf) {
        return buf.writeByte(MAGIC).writeByte(byteOne).writeByte(byteTwo);
    }

    static MessageType createUnknown(byte one, byte two) {
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
