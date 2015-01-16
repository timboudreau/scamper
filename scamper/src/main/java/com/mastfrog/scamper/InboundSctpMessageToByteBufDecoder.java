package com.mastfrog.scamper;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import static com.mastfrog.scamper.FragmentedMessageOverflowHandler.DEFAULT_MAX_AGGREGATED_BYTES;
import static com.mastfrog.scamper.FragmentedMessageOverflowHandler.SETTINGS_KEY_MAX_AGGREGATED_BYTES;
import com.mastfrog.settings.Settings;
import com.sun.nio.sctp.MessageInfo;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Converts inbound SctpMessages into ByteBufs, and stores the inbound sctp
 * stream identifier as a channnel attribute keyed to SCTP_CHANNEL_KEY. This is
 * so that third-party codecs that operate on raw ByteBufs can be involved in
 * the pipeline (ideally SslHandler can be used off-the-shelf).
 *
 * @author Tim Boudreau
 */
@Singleton
@ChannelHandler.Sharable
final class InboundSctpMessageToByteBufDecoder extends SimpleChannelInboundHandler<SctpMessage> {

    public static final AttributeKey<Integer> SCTP_CHANNEL_KEY = AttributeKey.valueOf(InboundSctpMessageToByteBufDecoder.class, "sctpChannel");
    private final Associations assoc;

    private static final AttributeKey<Fragments> QUEUE_KEY = AttributeKey.valueOf(InboundSctpMessageToByteBufDecoder.class, "queue");

    private final long maxBytes;
    private final FragmentedMessageOverflowHandler overflowHandler;

    @Inject
    InboundSctpMessageToByteBufDecoder(Associations assoc, Settings settings, FragmentedMessageOverflowHandler overflowHandler) {
        super(SctpMessage.class);
        maxBytes = settings.getLong(SETTINGS_KEY_MAX_AGGREGATED_BYTES, DEFAULT_MAX_AGGREGATED_BYTES);
        this.assoc = assoc;
        this.overflowHandler = overflowHandler;
    }

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, SctpMessage msg) throws Exception {
        ctx.attr(SCTP_CHANNEL_KEY).set(msg.streamIdentifier());
        Attribute<Fragments> fragmentsAttr = ctx.attr(QUEUE_KEY);
        Fragments fragments = fragmentsAttr.get();
        if (fragments == null) {
            fragmentsAttr.set(fragments = new Fragments());
            ctx.channel().closeFuture().addListener(fragments);
        }
        ByteBuf aggregated = fragments.contentFor(ctx, msg);
        // aggregated will be null if !msg.isComplete() - the
        // messages will be queued.
        if (aggregated != null) {
            aggregated.retain();
            ctx.fireChannelRead(aggregated);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            int sctpStream = assoc.nextInStream(ctx.channel());
            NioSctpChannel ch = (NioSctpChannel) ctx.channel();
            MessageInfo info = MessageInfo.createOutgoing(ch.association(), ch.remoteAddress(), sctpStream);
            info.unordered(true);
            msg = new SctpMessage(info, buf);
        }
        super.write(ctx, msg, promise);
    }

    private final class Fragments implements ChannelFutureListener {

        private final Map<Integer, BufferQueue> buffers = Maps.newConcurrentMap();

        public ByteBuf contentFor(ChannelHandlerContext ctx, SctpMessage msg) {
            // In theory, the protocol stack is supposed to be de-fragmenting
            // messages before they ever get to the application.
            // In reality, that's not happpening.
            BufferQueue queue = buffers.get(msg.streamIdentifier());
            if (queue == null) {
                queue = new BufferQueue();
                buffers.put(msg.streamIdentifier(), queue);
            }
            if (!msg.isComplete()) {
                // Add it to the queue, getting back the total bytes we are
                // retaining
                long byteCount = queue.add(msg.content().retain());
                if (byteCount > maxBytes) {
                    // Don't pass the queue, but the buffers, so the overflow
                    // handler can iterate them without clearing them
                    boolean dump = overflowHandler.onTooManyFragmentedBytes(ctx, byteCount, msg, queue.bufs);
                    if (dump) {
                        for (ByteBuf buf : queue) { // iterating clears the queue
                            buf.release();
                        }
                    }
                }
                return null;
            }
            // No queued messages - we got a complete message, so just send it on
            if (queue.isEmpty()) {
                return msg.content();
            }
            // Merge the queued byte buffers into a composite buffer
            CompositeByteBuf buf = ctx.alloc().compositeBuffer(queue.size() + 1);
            int ix = 0;
            for (ByteBuf component : queue) { // iterating clears the queue
                ix += component.readableBytes();
                buf.addComponent(component);
            }
            msg.content().retain();
            ix += msg.content().readableBytes();
            buf.addComponent(msg.content());
            buf.writerIndex(ix);
            return buf;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            buffers.clear();
        }
    }

    static class BufferQueue implements Iterable<ByteBuf> {

        private List<ByteBuf> bufs = Lists.newCopyOnWriteArrayList();
        private long byteCount;

        long add(ByteBuf buf) {
            byteCount += buf.readableBytes();
            bufs.add(buf);
            return byteCount;
        }

        boolean isEmpty() {
            return bufs == null || bufs.isEmpty();
        }

        int size() {
            return bufs.size();
        }

        @Override
        public Iterator<ByteBuf> iterator() {
            // If you want to iterate, then you're using the bytes and we
            // don't need to keep them
            List<ByteBuf> old = bufs;
            bufs = Lists.newCopyOnWriteArrayList();
            return old.iterator();
        }
    }
}
