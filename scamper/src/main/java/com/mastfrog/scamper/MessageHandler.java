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

import io.netty.channel.ChannelHandlerContext;

/**
 * Handles inbound messages, and can optionally reply with a message.
 * Annotate your implementation with &#064;Singleton if you do not want one
 * created for each message. You can also ask for an instance of Sender to
 * be created 
 *
 * @author Tim Boudreau
 */
public abstract class MessageHandler<T, M> {

    private final Class<M> messageType;

    /**
     * Create a new message handler.
     * @param messageType The type of object that should be decoded from
     * the bytes of the inbound message packet subsequent to the message type
     * header.
     */
    public MessageHandler(Class<M> messageType) {
        this.messageType = messageType;
    }

    /**
     * Called when an inbound message is received.
     * 
     * @param data The message to send
     * @param ctx The channel context, from which the remote address and
     * other information can be retrieved.
     * @return A message to send in reply, or null to send no reply.
     */
    public abstract Message<T> onMessage(Message<M> data, ChannelHandlerContext ctx);

    public Class<M> messageType() {
        return messageType;
    }
}
