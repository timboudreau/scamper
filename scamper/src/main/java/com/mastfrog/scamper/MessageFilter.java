package com.mastfrog.scamper;

import com.google.inject.ImplementedBy;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
@ImplementedBy(DefaultMessageFilter.class)
public interface MessageFilter {
    default <T, M> Message<T> handleMessage(Message<M> message, MessageHandler<T, M> handler, ChannelHandlerContext ctx) throws IOException {
        return handler.onMessage(message, ctx);
    }
}
