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
import io.netty.channel.sctp.SctpMessage;

/**
 * Selectively uses CompressingCodec for messages longer than a threshold
 * number of bytes.
 *
 * @author Tim Boudreau
 */
final class AutoCompressCodec extends MessageCodec {

    private final RawMessageCodec raw;
    private final CompressingCodec compress;
    private final int threshold;

    @Inject
    AutoCompressCodec(RawMessageCodec raw, CompressingCodec compress, Settings settings) {
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
