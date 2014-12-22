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
package com.mastfrog.scamper.protocol;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.util.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.sctp.SctpMessage;
import java.io.IOException;

/**
 * Receives reads and writes of SCTP messages and routes to the 
 * appropriate MessageHandlers.
 *
 * @author Tim Boudreau
 */
@Singleton
@Sharable
class MessageAdapter extends ChannelHandlerAdapter {

    private final Dependencies deps;

    private final Codec mapper;
    private final MessageTypeRegistry messageTypes;
    private final MessageHandlerMapping mapping;
    private final Sender sender;

    @Inject
    public MessageAdapter(MessageTypeRegistry messageTypes, MessageHandlerMapping mapping, Dependencies deps, Codec mapper, Sender sender) {
        this.deps = deps;
        this.mapper = mapper;
        this.messageTypes = messageTypes;
        this.mapping = mapping;
        this.sender = sender;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("ChannelActive " + ctx.channel().remoteAddress());
        super.channelActive(ctx); //To change body of generated methods, choose Tools | Templates.
    }

    private Message<?> handleMessage(ByteBuf buf, ChannelHandlerContext ctx) throws IOException {
        MessageType messageType = messageTypes.forByteBuf(buf);
        MessageHandler<?, ?> result = deps.getInstance(mapping.get(messageType));
        return handleMessage(messageType, result, buf, ctx);
    }

    private <T, M> Message<T> handleMessage(MessageType messageType, MessageHandler<T, M> handler, ByteBuf buf, ChannelHandlerContext ctx) throws IOException {
        Class<M> type = handler.messageType();
        Message<M> theMessage;
        if (type == ByteBuf.class) {
            theMessage = messageType.newMessage(type.cast(buf));
        } else if (type == Void.class) {
            theMessage = messageType.newMessage(null);
        } else {
            int bytes = buf.readableBytes();
            System.out.println("TO READ: " + bytes);
            try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
                M arg = mapper.readValue(in, type);
                System.out.println("Decoded " + arg + " for " + type);
                theMessage = messageType.newMessage(arg);
            }
        }
        return handler.onMessage(theMessage, ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        SctpMessage sctpMsg = (SctpMessage) msg;
        ByteBuf buf = sctpMsg.content();
        Message<?> result = handleMessage(buf, ctx);
        if (result != null) {
            sender.send(ctx.channel(), result, 0);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        System.out.println("Channel write " + msg.getClass().getName() + " to " + ctx.channel().remoteAddress());
        super.write(ctx, msg, promise); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        cause.printStackTrace();
        ctx.close();
    }
}
