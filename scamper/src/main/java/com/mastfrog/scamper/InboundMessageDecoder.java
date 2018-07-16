package com.mastfrog.scamper;

import com.fasterxml.jackson.core.JsonParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.giulius.Dependencies;
import static com.mastfrog.scamper.ProtocolModule.GUICE_BINDING_SCAMPER_CODEC;
import com.mastfrog.util.codec.Codec;
import com.mastfrog.util.streams.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;

/**
 * Takes incoming MessageTypeAndBuffer, looks up the handler and processes them.
 *
 * @author Tim Boudreau
 */
@Singleton
@ChannelHandler.Sharable
final class InboundMessageDecoder extends Netty5Handler<MessageTypeAndBuffer> {

    private final MessageHandlerMapping mapping;
    private final Dependencies deps;
    private final Codec mapper;

    @Inject
    InboundMessageDecoder(MessageHandlerMapping mapping, Dependencies deps, @Named(GUICE_BINDING_SCAMPER_CODEC) Codec mapper) {
        this.mapping = mapping;
        this.deps = deps;
        this.mapper = mapper;
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    private Message<?> handleMessage(MessageTypeAndBuffer typeAndPayload, ChannelHandlerContext ctx) throws IOException {
        MessageType messageType = typeAndPayload.messageType;
        MessageHandler<?, ?> result = deps.getInstance(mapping.get(messageType));
        return decode(messageType, result.messageType(), typeAndPayload.buf, ctx);
    }

    private <T, M> Message<M> decode(MessageType messageType, Class<M> type, ByteBuf buf, ChannelHandlerContext ctx) throws IOException {
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
        return theMessage;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, MessageTypeAndBuffer decoded) throws Exception {
        // PENDING: Give MessageHandler a way to be handed the ChannelFuture from the send,
        // and or receive a reply
        Message<?> message = handleMessage(decoded, ctx);
        if (message != null) {
            ctx.fireChannelRead(message);
        }
    }
}
