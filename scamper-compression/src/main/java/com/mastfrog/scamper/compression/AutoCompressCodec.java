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
import com.mastfrog.scamper.codec.MessageCodec;
import com.mastfrog.scamper.codec.RawMessageCodec;
import static com.mastfrog.scamper.compression.CompressionModule.DEFAULT_COMPRESSION_THRESHOLD;
import static com.mastfrog.scamper.compression.CompressionModule.SETTINGS_KEY_COMPRESSION_THRESHOLD;
import com.mastfrog.settings.Settings;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;

/**
 * Selectively uses CompressingCodec for messages longer than a threshold
 * number of bytes.
 *
 * @author Tim Boudreau
 */
final class AutoCompressCodec extends MessageCodec {

    private final MessageCodec raw;
    private final CompressingCodec compress;
    private final int threshold;

    @Inject
    AutoCompressCodec(RawMessageCodec raw, CompressingCodec compress, Settings settings) {
        this((MessageCodec) raw, compress, settings);
    }
    
    public AutoCompressCodec(MessageCodec raw, CompressingCodec compress, Settings settings) {
        this.raw = raw;
        this.compress = compress;
        threshold = settings.getInt(SETTINGS_KEY_COMPRESSION_THRESHOLD, DEFAULT_COMPRESSION_THRESHOLD);
    }

    @Override
    public MessageTypeAndBuffer decode(ByteBuf message, ChannelHandlerContext ctx, int sctpChannel) {
        return compress.decode(message, ctx, sctpChannel);
    }

    @Override
    public ByteBuf encode(MessageType type, ByteBuf outbound, Channel channel) {
        if (outbound.readableBytes() > threshold) {
            return compress.encode(type, outbound, channel);
        } else {
            return raw.encode(type, outbound, channel);
        }
    }

    @Override
    public int magicNumber() {
        throw new UnsupportedOperationException("Compound codec does not have a specific magic number.");
    }

    @Override
    public boolean accept(ByteBuf data) {
        int ix = data.readerIndex();
        boolean result = raw.accept(data) || compress.accept(data);
        if (!result) {
            data.readerIndex(ix);
        }
        return result;
    }
    
}
