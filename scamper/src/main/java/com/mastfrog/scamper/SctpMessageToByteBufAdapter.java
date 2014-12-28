package com.mastfrog.scamper;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.sctp.SctpMessage;

/**
 *
 * @author Tim Boudreau
 */
public class SctpMessageToByteBufAdapter extends SimpleChannelInboundHandler<SctpMessage> {
    
    SctpMessageToByteBufAdapter() {
        super(SctpMessage.class);
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, SctpMessage msg) throws Exception {
//        ctx.write(msg.content());
        super.userEventTriggered(ctx, msg.content());
    }
}
