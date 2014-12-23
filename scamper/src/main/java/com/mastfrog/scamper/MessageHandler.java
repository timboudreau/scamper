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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * Handles inbound messages, and can optionally reply with a message. Annotate
 * your implementation with &#064;Singleton if you do not want one created for
 * each message.
 * <p>
 * If you need to send messages to other servers, or send responses
 * asynchronously after doing something else, ask for an instance of
 * {@link Sender} to be passed as a constructor argument - see the
 * {@link MessageHandler.Raw} nested class for a convenient way to do that.
 *
 * @author Tim Boudreau
 */
public abstract class MessageHandler<T, M> {

    /**
     * The message type.
     */
    private final Class<M> messageType;

    /**
     * Create a new message handler.
     *
     * @param payloadType The type of object that should be decoded from the
     * bytes of the inbound message packet subsequent to the message type
     * header. This is needed so the library can decode the right type from the
     * ByteBuf payload of the SctpMessage. If you do not <i>want</i> any
     * decoding done, simply use <code>ByteBuf</code> as the type, and the raw
     * bytes will be passed as the message payload.
     */
    public MessageHandler(Class<M> payloadType) {
        this.messageType = payloadType;
    }

    /**
     * Called when an inbound message is received.
     *
     * @param data The message to send
     * @param ctx The channel context, from which the remote address and other
     * information can be retrieved.
     * @return A message to send in reply, or null to send no reply. If the
     * return message's payload is of any type other than ByteBuf, it will be
     * encoded using Jackson (by default to BSON) before sending.
     */
    public abstract Message<T> onMessage(Message<M> data, ChannelHandlerContext ctx);

    /**
     * Get the message type - the type of the payload of the {@link Message}
     * passed to <code>onMessage()</code>.
     *
     * @return The type
     */
    public final Class<M> messageType() {
        return messageType;
    }

    /**
     * Convenience implementation of {@link MessageHandler} that receives the
     * raw message bytes with no decoding.
     */
    public static abstract class Raw extends MessageHandler<Object, ByteBuf> {

        protected Raw() {
            super(ByteBuf.class);
        }

        @Override
        public abstract Message<Object> onMessage(Message<ByteBuf> data, ChannelHandlerContext ctx);
    }
}
