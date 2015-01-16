package com.mastfrog.scamper;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.sctp.SctpMessage;

/**
 * SCTP packets can be fragmented, and we aggregate them in that case. The
 * settings key <code>sctp.max.aggregated.bytes</code> determines the maximum
 * number of bytes we will cache - if we go over that amount, this class will be
 * called. The default behavior is to dump the queue and close the channel.
 * <p>
 * Bind your subclass with Guice if you want different behavior (for example, to
 * send a failure message to the client).
 *
 * @author Tim Boudreau
 */
public class FragmentedMessageOverflowHandler {

    public static final String SETTINGS_KEY_MAX_AGGREGATED_BYTES = "sctp.max.aggregated.bytes";
    public static final int DEFAULT_MAX_AGGREGATED_BYTES = 32768 * 4;

    protected FragmentedMessageOverflowHandler() {

    }

    /**
     * Called if the number of bytes queued for a particular sctp channel is
     * larger than the set limit.  The default implementation simply
     * closes the connection and instructs the caller to discard the buffers.
     *
     * @param ctx The channel context
     * @param totalBytes The number of bytes queued, including those in the
     * current message
     * @param currentMessage The current message
     * @param buffers The buffers
     * @return true if the buffers should be discarded.  If you return false,
     * the condition will be ignored and an abusive client could run you out
     * of memory.
     */
    protected boolean onTooManyFragmentedBytes(ChannelHandlerContext ctx, long totalBytes, 
            SctpMessage currentMessage, Iterable<ByteBuf> buffers) {
        ctx.close();
        return true;
    }
}
