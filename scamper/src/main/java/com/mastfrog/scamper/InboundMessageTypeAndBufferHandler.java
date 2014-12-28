package com.mastfrog.scamper;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.util.Codec;
import com.mastfrog.util.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.io.IOException;

/**
 * Takes incoming MessageTypeAndBuffer, looks up the handler and processes
 * them.
 *
 * @author Tim Boudreau
 */
@Singleton
@ChannelHandler.Sharable
final class InboundMessageTypeAndBufferHandler extends SimpleChannelInboundHandler<MessageTypeAndBuffer> {

    private final MessageHandlerMapping mapping;
    private final Dependencies deps;
    private final Codec mapper;
    private final Sender sender;

    @Inject
    InboundMessageTypeAndBufferHandler(MessageHandlerMapping mapping, Dependencies deps, Codec mapper, Sender sender) {
        this.mapping = mapping;
        this.deps = deps;
        this.mapper = mapper;
        this.sender = sender;
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    private Message<?> handleMessage(MessageTypeAndBuffer typeAndPayload, ChannelHandlerContext ctx) throws IOException {
        MessageType messageType = typeAndPayload.messageType;
        MessageHandler<?, ?> result = deps.getInstance(mapping.get(messageType));
        return handleMessage(messageType, result, typeAndPayload.buf, ctx);
    }

    private <T, M> Message<T> handleMessage(MessageType messageType, MessageHandler<T, M> handler, ByteBuf buf, ChannelHandlerContext ctx) throws IOException {
        Class<M> type = handler.messageType();
        Message<M> theMessage;
        if (type == ByteBuf.class) {
            theMessage = messageType.newMessage(type.cast(buf));
        } else if (type == Void.class) {
            theMessage = messageType.newMessage(null);
        } else {
            try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
                M arg = mapper.readValue(in, type);
                theMessage = messageType.newMessage(arg);
            } catch (JsonParseException ex) {
                buf.resetReaderIndex();
                try (ByteBufInputStream in2 = new ByteBufInputStream(buf)) {
                    throw new IOException("Invalid JSON: '" + Streams.readString(in2, 256) + "'", ex);
                }
            } finally {
                buf.discardReadBytes();
            }
        }
        return handler.onMessage(theMessage, ctx);
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, MessageTypeAndBuffer decoded) throws Exception {
        // PENDING: Give MessageHandler a way to be handed the ChannelFuture from the send,
        // and or receive a reply
        Message<?> result = handleMessage(decoded, ctx);
        if (result != null) {
            sender.send(ctx.channel(), result, decoded.streamIdentifier);
        }
    }
}
