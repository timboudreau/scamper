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

import com.mastfrog.scamper.codec.MessageCodec;
import com.fasterxml.jackson.core.JsonParseException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.util.Codec;
import com.mastfrog.util.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.sctp.SctpMessage;
import java.io.IOException;
import java.net.SocketAddress;

/**
 * Receives reads and writes of SCTP messages and routes to the appropriate
 * MessageHandlers.
 *
 * @author Tim Boudreau
 */
@Singleton
@Sharable
final class InboundMessageDispatcher extends SimpleChannelInboundHandler<SctpMessage> {

    private final Dependencies deps;

    private final Codec mapper;
    private final MessageHandlerMapping mapping;
    private final Sender sender;
    private final ErrorHandler errors;
    private final MessageCodec codec;
    private final Associations assoc;

    @Inject
    public InboundMessageDispatcher(MessageHandlerMapping mapping, Dependencies deps, Codec mapper, Sender sender, ErrorHandler errors, MessageCodec codec, Associations assoc) {
        this.deps = deps;
        this.mapper = mapper;
        this.mapping = mapping;
        this.sender = sender;
        this.errors = errors;
        this.codec = codec;
        this.assoc = assoc;
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        codec.onChannelActive(ctx);
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, SctpMessage sctpMsg) throws Exception {
        assoc.ensureRegistered(ctx);
        MessageTypeAndBuffer decoded = codec.decode(sctpMsg, ctx);
        ctx.fireChannelRead(decoded);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        super.write(ctx, msg, promise); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        errors.onError(ctx, cause);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        codec.onClose(ctx, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        codec.onConnect(ctx, remoteAddress, localAddress, promise);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        codec.onChannelUnregistered(ctx);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        codec.onChannelRegistered(ctx);
    }
}
