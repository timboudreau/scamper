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

import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;
import com.mastfrog.util.Checks;
import com.mastfrog.util.ConfigurationError;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps message types to handlers
 *
 * @author Tim Boudreau
 */
final class MessageHandlerMapping {

    private final Map<MessageType, Class<? extends MessageHandler<?, ?>>> map;

    private MessageHandlerMapping(Map<MessageType, Class<? extends MessageHandler<?, ?>>> map) {
        this.map = ImmutableMap.copyOf(map);
    }

    public Class<? extends MessageHandler<?, ?>> get(MessageType type) {
        Class<? extends MessageHandler<?, ?>> handler = map.get(type);
        return handler == null ? NullHandler.class : handler;
    }

    @Singleton
    private static final class NullHandler extends MessageHandler<Void, ByteBuf> {

        NullHandler() {
            super(ByteBuf.class);
        }

        @Override
        public Message<Void> onMessage(Message<ByteBuf> data, ChannelHandlerContext ctx) {
            System.err.println("Discarding unknown message " + data.type + " - " + data.body);
            ctx.channel().close();
            if (data.body != null) {
                data.body.release();
            }
            return null;
        }
    }

    static class Builder {

        private final Map<MessageType, Class<? extends MessageHandler<?, ?>>> map = new HashMap<>();

        public Builder add(MessageType type, Class<? extends MessageHandler<?, ?>> handler) {
            Checks.notNull("type", type);
            Checks.notNull("handler", handler);
            if (map.containsKey(type)) {
                throw new ConfigurationError("Already contains " + type);
            }
            map.put(type, handler);
            return this;
        }

        public MessageHandlerMapping build() {
            return new MessageHandlerMapping(map);
        }
    }
}
