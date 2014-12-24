package com.mastfrog.scamper;

import io.netty.buffer.ByteBuf;

/**
 *
 * @author Tim Boudreau
 */
public class MessageTypeAndBuffer {

    public final MessageType messageType;
    public final ByteBuf buf;

    public MessageTypeAndBuffer(MessageType message, ByteBuf buf) {
        this.messageType = message;
        this.buf = buf;
    }
}
