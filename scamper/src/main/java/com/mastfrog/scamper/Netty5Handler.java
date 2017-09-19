/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mastfrog.scamper;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import java.net.SocketAddress;

/**
 * This library was originally written against Netty 5 and then backed up to Netty 4,
 * so this adapter allows for fewer modifications to the original code.
 *
 * @author Tim Boudreau
 */
public abstract class Netty5Handler<T> extends SimpleChannelInboundHandler<T> implements ChannelOutboundHandler {

    public Netty5Handler() {
    }

    public Netty5Handler(boolean autoRelease) {
        super(autoRelease);
    }

    public Netty5Handler(Class<? extends T> inboundMessageType) {
        super(inboundMessageType);
    }

    public Netty5Handler(Class<? extends T> inboundMessageType, boolean autoRelease) {
        super(inboundMessageType, autoRelease);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext chc, T i) throws Exception {
        messageReceived(chc, i);
    }

    protected abstract void messageReceived(ChannelHandlerContext chc, T i) throws Exception;

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress,
            ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
            SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }
    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise)
            throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise)
            throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ctx.write(msg, promise);
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
}
