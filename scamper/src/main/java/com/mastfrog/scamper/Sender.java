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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import static com.mastfrog.scamper.ProtocolModule.GUICE_BINDING_SCAMPER_CODEC;
import com.mastfrog.util.Checks;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for sending messages.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class Sender {

    private final Associations associations;
    private final Codec mapper;
    private final MessageCodec encoder;
    private static final Logger logger = Logger.getLogger(Sender.class.getName());

    @Inject
    public Sender(Associations associations, @Named(GUICE_BINDING_SCAMPER_CODEC) Codec bsonJson, MessageCodec codec) {
        this.associations = associations;
        this.mapper = bsonJson;
        this.encoder = codec;
    }

    /**
     * Send a message using the passed channel.
     *
     * @param channel A channel
     * @param message A message
     * @return a future that will be notified when the message write is
     * completed
     * @throws IOException if something goes wrong
     */
    public ChannelFuture send(Channel channel, final Message<?> message) throws IOException {
        return send(channel, message, associations.nextOutStream(channel));
    }

    /**
     * Send a message using the passed channel.
     *
     * @param channel The channel
     * @param message A future which will be notified when the message is
     * flushed to the socket
     * @param sctpChannel The ordinal of the sctp channel
     * @return a future that will be notified when the message write is
     * completed
     * @throws IOException if something goes wrong
     */
    @SuppressWarnings("unchecked")
    public ChannelFuture send(Channel channel, final Message<?> message, int sctpChannel) throws IOException {
        Checks.notNull("channel", channel);
        Checks.notNull("message", message);
        Checks.nonNegative("sctpChannel", sctpChannel);
        ByteBufAllocator alloc = channel.alloc();
        ByteBuf outbound = alloc.buffer();
        if (message.body != null) {
            if (message.body instanceof ByteBuf) {
                outbound = (ByteBuf) message.body;
            } else {
                outbound = alloc.buffer();
                try (ByteBufOutputStream out = new ByteBufOutputStream(outbound)) {
                    mapper.writeValue(message.body, out);
                }
            }
        }
        ByteBuf encodedBuffer = encoder.encode(message.type, outbound, channel);
        NioSctpChannel ch = (NioSctpChannel) channel;
        if (!ch.isOpen()) {
            return ch.newFailedFuture(new ClosedChannelException());
        }
        if (ch.association() == null) {
            return channel.newFailedFuture(new IOException("Association closed - client has disconnected"));
        }
        MessageInfo info = MessageInfo.createOutgoing(ch.association(), ch.remoteAddress(), sctpChannel);
        info.unordered(true);

        SctpMessage sctpMessage = new SctpMessage(info, encodedBuffer);
        logger.log(Level.FINE, "Send message to {0} type {1}", new Object[]{channel.remoteAddress(),
            message.type});
        ChannelFuture result = channel.writeAndFlush(sctpMessage);
        if (logger.isLoggable(Level.FINER)) {
            result.addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.cause() != null) {
                        logger.log(Level.SEVERE, "Send to " + ch.remoteAddress() + " failed", future.cause());
                    } else {
                        logger.log(Level.FINER, "Send completed to {0}", ch.remoteAddress());
                    }
                }

            });
        }
        return result;
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

    /**
     * Send a message using the passed channel.
     *
     * @param address The address
     * @param message A future which will be notified when the message is
     * flushed to the socket
     * @param l A ChannelFutureListener to be notified when the mesage is
     * flushed (remember to check <code>ChannelFuture.getCause()</code> to check
     * for failure)
     * @return a future that will be notified when the message write is
     * completed
     */
    public ChannelFuture send(Address address, final Message<?> message, final ChannelFutureListener l) {
        int sctpChannel = associations.nextOutStream(address);
        return send(address, message, sctpChannel, l);
    }

    /**
     * Send a message using the passed channel.
     *
     * @param address The address
     * @param message A future which will be notified when the message is
     * flushed to the socket
     * @param sctpChannel The ordinal of the sctp channel
     * @param l A ChannelFutureListener to be notified when the mesage is
     * flushed (remember to check <code>ChannelFuture.getCause()</code> to check
     * for failure)
     * @return a future that will be notified when the message write is
     * completed
     */
    public ChannelFuture send(final Address address, final Message<?> message, final int sctpChannel, final ChannelFutureListener l) {
        Checks.notNull("address", address);
        Checks.notNull("message", message);
        Checks.nonNegative("sctpChannel", sctpChannel);
        logger.log(Level.FINE, "Send message to {0} on {1} type {1}", new Object[]{address, sctpChannel,
            message.type});
        return associations.connect(address).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.cause() == null) {
                    logger.log(Level.FINE, "Got back connection {0} for {1}", new Object[]{future.channel().remoteAddress(), address});
                } else {
                    if (l != null) {
                        l.operationComplete(future);
                    }
                    return;
                }
                ChannelFuture fut = send(future.channel(), message, sctpChannel);
                if (l != null) {
                    fut.addListener(l);
                }
            }
        });
    }
}
