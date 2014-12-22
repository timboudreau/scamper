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
import com.mastfrog.scamper.Address;
import com.mastfrog.scamper.Associations;
import com.mastfrog.util.Codec;
import com.sun.nio.sctp.MessageInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.nio.NioSctpChannel;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;

/**
 * Utility for sending messages.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class Sender {

    private final Associations associations;
    private final Codec mapper;

    @Inject
    public Sender(Associations associations, Codec mapper) {
        this.associations = associations;
        this.mapper = mapper;
    }

    /**
     * Send a message using the passed channel.
     *
     * @param channel A channel
     * @param result A message
     * @return a future that will be notified when the message write is
     * completed
     * @throws IOException if something goes wrong
     */
    public ChannelFuture send(Channel channel, final Message<?> result) throws IOException {
        return send(channel, result, associations.nextOutStream(channel));
    }

    public ChannelFuture send(Channel channel, final Message<?> result, int sctpChannel) throws IOException {
        ByteBufAllocator alloc = channel.alloc();
        ByteBuf outbound = alloc.buffer();
        result.type.writeHeader(outbound);
        if (result.body != null) {
            if (result.body instanceof ByteBuf) {
                result.type.writeHeader(outbound);
                outbound.writeBytes((ByteBuf) result.body);
            } else {
                result.type.writeHeader(outbound);
                try (ByteBufOutputStream out = new ByteBufOutputStream(outbound)) {
                    System.out.println("WRITE BODY " + result.body);
                    mapper.writeValue(result.body, out);
                }
            }
        }
        NioSctpChannel ch = (NioSctpChannel) channel;
        if (!ch.isOpen()) {
            return ch.newFailedFuture(new ClosedChannelException());
        }
        MessageInfo info = MessageInfo.createOutgoing(ch.association(), ch.remoteAddress(), sctpChannel);
        info.unordered(true);
        SctpMessage sctpMessage = new SctpMessage(info, outbound);
        return channel.writeAndFlush(sctpMessage);
    }

    /**
     * Send to an ad-hoc address. A new connection will be created if
     * necessary..
     *
     * @param address The address
     * @param message The message
     * @return this
     */
    public ChannelFuture send(Address address, final Message<?> message) {
        return send(address, message, null);
    }

    public ChannelFuture send(Address address, final Message<?> message, final ChannelFutureListener l) {
        int sctpChannel = associations.nextOutStream(address);
        return send(address, message, sctpChannel, l);
    }

    public ChannelFuture send(Address address, final Message<?> message, final int sctpChannel, final ChannelFutureListener l) {
        return associations.connect(address).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                ChannelFuture fut = send(future.channel(), message, sctpChannel);
                if (l != null) {
                    fut.addListener(l);
                }
            }
        });
    }
}
