/*
 * Copyright (c) 2015 Tim Boudreau
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
package com.mastfrog.scamper.compression;

import com.google.inject.Inject;
import com.mastfrog.scamper.MessageType;
import com.mastfrog.scamper.MessageTypeAndBuffer;
import com.mastfrog.scamper.MessageTypeRegistry;
import com.mastfrog.scamper.codec.MessageCodec;
import com.mastfrog.scamper.codec.RawMessageCodec;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Implements gzip compression over messages with a different magic number for
 * the first byte of the packet.
 *
 * @author Tim Boudreau
 */
final class CompressingCodec extends MessageCodec {

    private final MessageCodec raw;
    private static final int MAGIC = 124;
    private final MessageTypeRegistry reg;
    private final int level;

    @Inject
    CompressingCodec(RawMessageCodec raw, MessageTypeRegistry reg, Settings settings) {
        this((MessageCodec) raw, reg, settings);
    }

    public CompressingCodec(MessageCodec raw, MessageTypeRegistry reg, Settings settings) {
        this.raw = raw;
        this.reg = reg;
        level = settings.getInt(CompressionModule.SETTINGS_KEY_GZIP_LEVEL, CompressionModule.DEFAULT_GZIP_COMPRESSION_LEVEL);
        if (level < 0 || level > 9) {
            throw new IllegalArgumentException(CompressionModule.SETTINGS_KEY_GZIP_LEVEL + " must be between 0 and 9 but was " + level);
        }
    }

    @Override
    public MessageTypeAndBuffer decode(ByteBuf buf, ChannelHandlerContext ctx, int sctpChannel) {
        byte magic = buf.readByte();
        if (magic == magicNumber()) {
            try {
                return decodeImpl(buf, ctx, sctpChannel);
            } catch (Exception ex) {
                return Exceptions.chuck(ex);
            }
        } else {
            buf.resetReaderIndex();
            return raw.decode(buf, ctx, sctpChannel);
        }
    }

    @Override
    public ByteBuf encode(MessageType type, ByteBuf outbound, Channel channel) {
        try {
            ByteBuf head = channel.alloc().buffer(type.headerLength() + 1);
            head.writeByte(magicNumber());
            type.writeHeader(head);
            ByteBuf compressed = channel.alloc().buffer();
            compress(outbound, compressed);
            ByteBuf result = channel.alloc().compositeBuffer(2).addComponent(head).addComponent(compressed);
            result.writerIndex(head.readableBytes() + compressed.readableBytes());
            return result;
        } catch (Exception ex) {
            return Exceptions.chuck(ex);
        }
    }

    static String bb2s(ByteBuf buf) throws IOException {
        int old = buf.readerIndex();
        try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
            return Streams.readString(in);
        } finally {
            buf.readerIndex(old);
        }
    }

    @Override
    public int magicNumber() {
        return MAGIC;
    }

    private ByteBufAllocator alloc(ChannelHandlerContext ctx) {
        return ctx == null ? /* unit test */ ByteBufAllocator.DEFAULT : ctx.alloc();
    }

    private MessageTypeAndBuffer decodeImpl(ByteBuf buf, ChannelHandlerContext ctx, int sctpChannel) throws Exception {
        MessageType messageType = reg.forByteBuf(buf);
        if (messageType.isUnknown()) {
            return new MessageTypeAndBuffer(messageType, buf.slice(), sctpChannel);
        }
        ByteBuf bb = alloc(ctx).buffer();
        uncompress(buf.slice(), bb);
        return new MessageTypeAndBuffer(messageType, bb, sctpChannel);
    }

    protected void compress(ByteBuf in, ByteBuf out) throws IOException {
        try (GZIPOutputStream outStream = new GZIPOutputStream(new ByteBufOutputStream(out), level)) {
            try (ByteBufInputStream inStream = new ByteBufInputStream(in)) {
                Streams.copy(inStream, outStream, 512);
            }
        }
    }

    protected void uncompress(ByteBuf in, ByteBuf out) throws IOException {
        try (GZIPInputStream ins = new GZIPInputStream(new ByteBufInputStream(in))) {
            try (ByteBufOutputStream outs = new ByteBufOutputStream(out)) {
                Streams.copy(ins, outs, 512);
            }
        }
    }
}
