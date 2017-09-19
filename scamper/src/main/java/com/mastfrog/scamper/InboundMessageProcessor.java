package com.mastfrog.scamper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.giulius.Dependencies;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;

/**
 * Takes the decoded inbound message and hands it off to a registered
 * MessageHandler to process it, and if that returns a message, sends that back
 * down the pipeline.
 *
 * @author Tim Boudreau
 */
@Singleton
@ChannelHandler.Sharable
public class InboundMessageProcessor extends Netty5Handler<Message> {

    private final MessageHandlerMapping mapping;
    private final Dependencies deps;
    private final Sender sender;

    @Inject
    InboundMessageProcessor(MessageHandlerMapping mapping, Dependencies deps, Sender sender) {
        super(Message.class);
        this.mapping = mapping;
        this.deps = deps;
        this.sender = sender;
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    private <T, M> Message<T> handleMessage(Message<M> message, MessageHandler<T, M> handler, ChannelHandlerContext ctx) throws IOException {
        return handler.onMessage(message, ctx);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void messageReceived(ChannelHandlerContext ctx, Message msg) throws Exception {
        Class<? extends MessageHandler> handlerClass = mapping.get(msg.type);
        MessageHandler<?, ?> h = deps.getInstance(handlerClass);
        Message<?> result = handleMessage(msg, h, ctx);
        if (result != null) {
//            ctx.write(result);
            sender.send(ctx.channel(), result);
        }
    }
}
