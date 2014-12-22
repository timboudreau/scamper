package com.mastfrog.scamper.protocol;

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
class MessageHandlerMapping {

    private final Map<MessageType, Class<? extends MessageHandler<?, ?>>> map;

    private MessageHandlerMapping(Map<MessageType, Class<? extends MessageHandler<?, ?>>> map) {
        this.map = ImmutableMap.copyOf(map);
    }

    public Class<? extends MessageHandler> get(MessageType type) {
        Class<? extends MessageHandler> handler = map.get(type);
        return handler == null ? NullHandler.class : handler;
    }

    @Singleton
    private static final class NullHandler extends MessageHandler<Void, ByteBuf> {

        NullHandler() {
            super(ByteBuf.class);
        }

        @Override
        public Message<Void> onMessage(Message<ByteBuf> data, ChannelHandlerContext ctx) {
            System.err.println("UNKNOWN MESSAGE " + data.type);
            ctx.channel().close();
            if (data.body  != null) {
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
