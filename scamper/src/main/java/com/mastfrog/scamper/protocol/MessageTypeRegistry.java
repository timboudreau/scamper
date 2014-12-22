package com.mastfrog.scamper.protocol;

import com.google.common.collect.ImmutableSet;
import io.netty.buffer.ByteBuf;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
class MessageTypeRegistry {

    private final Set<MessageType> types;
    public static final byte MAGIC = 123;

    MessageTypeRegistry(Set<MessageType> types) {
        this.types = ImmutableSet.copyOf(types);
    }

    void register(MessageType type) {
        types.add(type);
    }

    public MessageType forByteBuf(ByteBuf buf) {
        if (buf.readableBytes() >= 3) {
            byte zero = buf.readByte();
            if (zero == MAGIC) {
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
        }
        buf.resetReaderIndex();
        return MessageType.createUnknown((byte) 0, (byte) 0);
    }
}
