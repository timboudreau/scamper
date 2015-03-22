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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.scamper.MessageType;
import com.mastfrog.scamper.MessageTypeAndBuffer;
import com.mastfrog.scamper.MessageTypeRegistry;
import static com.mastfrog.scamper.ProtocolModule.GUICE_BINDING_SCAMPER_CODEC;
import com.mastfrog.util.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

/**
 * Encoder which simply sends the raw message.
 *
 * @author Tim Boudreau
 */
@Singleton
public class RawMessageCodec extends MessageCodec {

    protected final Codec jsonBson;
    protected final MessageTypeRegistry messageTypes;
    public static final int MAGIC = 123;

    @Inject
    protected RawMessageCodec(@Named(GUICE_BINDING_SCAMPER_CODEC) Codec jsonBson, MessageTypeRegistry messageTypes) {
        this.jsonBson = jsonBson;
        this.messageTypes = messageTypes;
    }

    @Override
    public int magicNumber() {
        return MAGIC;
    }

    @Override
    public MessageTypeAndBuffer decode(ByteBuf buf, ChannelHandlerContext ctx, int sctpChannel) {
        byte first = buf.readByte();
        if (first == magicNumber()) {
            MessageType messageType = messageTypes.forByteBuf(buf);
            return new MessageTypeAndBuffer(messageType, buf, sctpChannel);
        }
        return new MessageTypeAndBuffer(MessageType.createUnknown(-1, -1), buf.resetReaderIndex(), sctpChannel);
    }

    @Override
    public ByteBuf encode(MessageType type, ByteBuf outbound, Channel channel) {
        ByteBuf buf = channel.alloc().buffer(type.headerLength() + 1).writeByte(magicNumber());
        type.writeHeader(buf);
        ByteBuf result = channel.alloc().compositeBuffer(2).addComponent(buf).addComponent(outbound);
        result.writerIndex(buf.readableBytes() + outbound.readableBytes());
        return result;
    }
}
