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
package com.mastfrog.scamper.codec;

import com.google.inject.ImplementedBy;
import com.mastfrog.scamper.MessageType;
import com.mastfrog.scamper.MessageTypeAndBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.sctp.SctpMessage;
import java.net.SocketAddress;
import java.nio.channels.AlreadyConnectedException;

/**
 * Codec which is responsible for transforming a message type and message into
 * the final format that will be sent over the wire. The default (insecure)
 * implementation simply sends raw data. This class contains both encoding and
 * decoding methods, and hooks called when connections are opened and closed
 * which can be used to perform any handshaking necessary before the connection
 * can be used.
 *
 * @author Tim Boudreau
 */
@ImplementedBy(RawMessageCodec.class)
public abstract class MessageCodec {

    /**
     * Decode an SctpMessage into a MessageType and a payload ByteBuf. The
     * payload ByteBuf's reader index should be zero at the beginning of the
     * payload, not including any header data- use ByteBuf.slice() when
     * implementing.
     *
     * @param message The message
     * @param ctx The channel context
     * @return A message type and a buffer
     */
    public abstract MessageTypeAndBuffer decode(ByteBuf message, ChannelHandlerContext ctx, int sctpChannel);

    /**
     * Encode the passed message type and payload buffer into the format it
     * should be sent over the wire in.
     *
     * @param type The type of message
     * @param outbound The payload, encoded using the DataEncoding configured
     * @param channel The channel
     * @return A ByteBuf - if using CompositeBuffer, take care that the writer
     * index is set correctly
     */
    public abstract ByteBuf encode(MessageType type, ByteBuf outbound, Channel channel);

    /**
     * The first byte of a message, which identifies it as belonging to this
     * codec (there could be more than one).
     *
     * @return
     */
    protected abstract int magicNumber();

    /**
     * Determine if this codec recognizes this ByteBuf.
     * <p>
     * This method will move the reader index of the passed ByteBuf <i>forward
     * one byte</i>
     * if it returns true. If it returns false, the ByteBuf's state will be
     * unaltered.
     * <p>
     * The default implementation checks if the first byte equals the return
     * value of <code>magicNumber()</code>. Implementations that delegate
     * between multiple codecs should call this method for each until one
     * accepts it.
     *
     * @param data
     * @return
     */
    public boolean accept(ByteBuf data) {
        int old = data.readerIndex();
        byte b = data.readByte();
        boolean result = magicNumber() == b;
        if (!result) {
            data.readerIndex(old);
        }
        return result;
    }

    /**
     * Called when the channel becomes active
     *
     * @param ctx The context
     */
    public void onChannelActive(ChannelHandlerContext ctx) {
        ctx.fireChannelActive();
    }

    /**
     * Called when a channel is registered
     *
     * @param ctx The context
     */
    public void onChannelRegistered(ChannelHandlerContext ctx) {
        ctx.fireChannelUnregistered();
    }

    /**
     * Called when a channel is unregistered
     *
     * @param ctx The context
     */
    public void onChannelUnregistered(ChannelHandlerContext ctx) {
        ctx.fireChannelUnregistered();
    }

    /**
     * Called when the channel is closed
     *
     * @param ctx The context
     * @param promise A future
     * @throws Exception if something goes wrong
     */
    public void onClose(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    /**
     * Called when a connection is established, in order to perform any
     * handshaking needed
     *
     * @param ctx The context
     * @param remoteAddress The remote address
     * @param localAddress The local address
     * @param promise A future
     * @throws Exception if something goes wrong
     */
    public void onConnect(ChannelHandlerContext ctx, SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        try {
            System.out.println("OnConnect " + promise);
            if (!ctx.channel().isActive()) {
                ctx.connect(remoteAddress, localAddress, promise);
            } else {
                promise.setSuccess();
            }
        } catch (AlreadyConnectedException ex) {
            // ignore
        }
    }
}
