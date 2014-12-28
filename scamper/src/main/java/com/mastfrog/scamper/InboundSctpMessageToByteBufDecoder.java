package com.mastfrog.scamper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sun.nio.sctp.MessageInfo;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

/**
 * Converts inbound SctpMessages into ByteBufs, and stores the inbound sctp
 * stream identifier as a channnel attribute keyed to SCTP_CHANNEL_KEY. This is
 * so that third-party codecs that operate on raw ByteBufs can be involved in
 * the pipeline (ideally SslHandler can be used off-the-shelf).
 *
 * @author Tim Boudreau
 */
@Singleton
@ChannelHandler.Sharable
final class InboundSctpMessageToByteBufDecoder extends SimpleChannelInboundHandler<SctpMessage> {

    public static final AttributeKey<Integer> SCTP_CHANNEL_KEY = AttributeKey.valueOf(InboundSctpMessageToByteBufDecoder.class, "sctpChannel");
    private final Associations assoc;

    @Inject
    InboundSctpMessageToByteBufDecoder(Associations assoc) {
        super(SctpMessage.class);
        this.assoc = assoc;
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, SctpMessage msg) throws Exception {
        Attribute<Integer> sctpChannelAttribute = ctx.attr(SCTP_CHANNEL_KEY);
        sctpChannelAttribute.set(msg.streamIdentifier());
        msg.content().retain();
        ctx.fireChannelRead(msg.content());
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            int sctpStream = assoc.nextInStream(ctx.channel());
            NioSctpChannel ch = (NioSctpChannel) ctx.channel();
            MessageInfo info = MessageInfo.createOutgoing(ch.association(), ch.remoteAddress(), sctpStream);
            info.unordered(true);
            msg = new SctpMessage(info, buf);
        }
        super.write(ctx, msg, promise);
    }
}
