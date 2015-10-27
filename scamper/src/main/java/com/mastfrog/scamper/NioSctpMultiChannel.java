package com.mastfrog.scamper;

import com.sun.nio.sctp.Association;
import com.sun.nio.sctp.MessageInfo;
import com.sun.nio.sctp.NotificationHandler;
import com.sun.nio.sctp.SctpChannel;
import com.sun.nio.sctp.SctpMultiChannel;
import com.sun.nio.sctp.SctpStandardSocketOptions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import static io.netty.channel.ChannelOption.SO_RCVBUF;
import static io.netty.channel.ChannelOption.SO_SNDBUF;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.MessageSizeEstimator;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.nio.AbstractNioMessageChannel;
import io.netty.channel.sctp.DefaultSctpChannelConfig;
import io.netty.channel.sctp.SctpChannelConfig;
import static io.netty.channel.sctp.SctpChannelOption.SCTP_INIT_MAXSTREAMS;
import static io.netty.channel.sctp.SctpChannelOption.SCTP_NODELAY;
import io.netty.channel.sctp.SctpMessage;
import io.netty.channel.sctp.SctpNotificationHandler;
import io.netty.channel.sctp.SctpServerChannel;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Adapted from NioSctpChannel code in Netty
public class NioSctpMultiChannel extends AbstractNioMessageChannel implements io.netty.channel.sctp.SctpChannel {

    private static final ChannelMetadata METADATA = new ChannelMetadata(false);

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NioSctpChannel.class);

    private final SctpChannelConfig config;

    private final NotificationHandler<?> notificationHandler;

    private static SctpMultiChannel newSctpChannel() {
        try {
            return SctpMultiChannel.open();
        } catch (IOException e) {
            throw new ChannelException("Failed to open a sctp channel.", e);
        }
    }

    /**
     * Create a new instance
     */
    public NioSctpMultiChannel() {
        this(newSctpChannel());
    }

    /**
     * Create a new instance using {@link SctpChannel}
     */
    public NioSctpMultiChannel(SctpMultiChannel sctpChannel) {
        this(null, sctpChannel);
    }

    /**
     * Create a new instance
     *
     * @param parent the {@link Channel} which is the parent of this
     * {@link NioSctpChannel} or {@code null}.
     * @param sctpChannel the underlying {@link SctpChannel}
     */
    public NioSctpMultiChannel(Channel parent, SctpMultiChannel sctpChannel) {
        super(parent, sctpChannel, SelectionKey.OP_READ);
        try {
            sctpChannel.configureBlocking(false);
            config = new NioSctpChannelConfig(this, sctpChannel);
            notificationHandler = new SctpNotificationHandler(this);
        } catch (IOException e) {
            try {
                sctpChannel.close();
            } catch (IOException e2) {
                if (logger.isWarnEnabled()) {
                    logger.warn(
                            "Failed to close a partially initialized sctp channel.", e2);
                }
            }

            throw new ChannelException("Failed to enter non-blocking mode.", e);
        }
    }

    @Override
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) super.localAddress();
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return (InetSocketAddress) super.remoteAddress();
    }

    @Override
    public SctpServerChannel parent() {
        return (SctpServerChannel) super.parent();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    @Override
    public Association association() {
        try {
            return javaChannel().associations().isEmpty() ? null : javaChannel().associations().iterator().next();
        } catch (IOException ignored) {
            return null;
        } catch (NotYetBoundException ignored) {
            return null;
        }
    }

    @Override
    public Set<InetSocketAddress> allLocalAddresses() {
        try {
            final Set<SocketAddress> allLocalAddresses = javaChannel().getAllLocalAddresses();
            final Set<InetSocketAddress> addresses = new LinkedHashSet<InetSocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add((InetSocketAddress) socketAddress);
            }
            return addresses;
        } catch (Throwable ignored) {
            return Collections.emptySet();
        }
    }

    @Override
    public SctpChannelConfig config() {
        return config;
    }

    @Override
    public Set<InetSocketAddress> allRemoteAddresses() {
        try {
            Set<SocketAddress> all = new HashSet<SocketAddress>();
            for (Association a : javaChannel().associations()) {
                all.addAll(javaChannel().getRemoteAddresses(a));
            }
//            final Set<SocketAddress> allLocalAddresses = javaChannel().getAllLocalAddresses();
            final Set<SocketAddress> allLocalAddresses = all;
            final Set<InetSocketAddress> addresses = new HashSet<InetSocketAddress>(allLocalAddresses.size());
            for (SocketAddress socketAddress : allLocalAddresses) {
                addresses.add((InetSocketAddress) socketAddress);
            }
            return addresses;
        } catch (Throwable ignored) {
            return Collections.emptySet();
        }
    }

    @Override
    protected SctpMultiChannel javaChannel() {
        return (SctpMultiChannel) super.javaChannel();
    }

    @Override
    public boolean isActive() {
        SctpMultiChannel ch = javaChannel();
        return ch.isOpen() && association() != null;
    }

    @Override
    protected SocketAddress localAddress0() {
        try {
            Iterator<SocketAddress> i = javaChannel().getAllLocalAddresses().iterator();
            if (i.hasNext()) {
                return i.next();
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    protected SocketAddress remoteAddress0() {
        try {
            for (Association a : javaChannel().associations()) {
                Iterator<SocketAddress> i = javaChannel().getRemoteAddresses(a).iterator();
                if (i.hasNext()) {
                    return i.next();
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    @Override
    protected void doBind(SocketAddress localAddress) throws Exception {
        javaChannel().bind(localAddress);
    }

    @Override
    protected boolean doConnect(SocketAddress remoteAddress, SocketAddress localAddress) throws Exception {
        if (localAddress != null) {
            javaChannel().bind(localAddress);
        }

//        boolean success = false;
//        try {
//            boolean connected = javaChannel().connect(remoteAddress);
//            if (!connected) {
//                selectionKey().interestOps(SelectionKey.OP_CONNECT);
//            }
//            success = true;
//            return connected;
//        } finally {
//            if (!success) {
//                doClose();
//            }
//        }
        return true;
    }

    @Override
    protected void doFinishConnect() throws Exception {
//        if (!javaChannel().finishConnect()) {
//            throw new Error();
//        }
    }

    @Override
    protected void doDisconnect() throws Exception {
        doClose();
    }

    @Override
    protected void doClose() throws Exception {
        javaChannel().close();
    }

    @Override
    protected int doReadMessages(List<Object> buf) throws Exception {
        SctpMultiChannel ch = javaChannel();

        RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
        ByteBuf buffer = allocHandle.allocate(config().getAllocator());
        boolean free = true;
        try {
            ByteBuffer data = buffer.internalNioBuffer(buffer.writerIndex(), buffer.writableBytes());
            int pos = data.position();

            MessageInfo messageInfo = ch.receive(data, null, notificationHandler);
            if (messageInfo == null) {
                return 0;
            }

            allocHandle.lastBytesRead(data.position() - pos);
            buf.add(new SctpMessage(messageInfo,
                    buffer.writerIndex(buffer.writerIndex() + allocHandle.lastBytesRead())));
            free = false;
            return 1;
        } catch (Throwable cause) {
            PlatformDependent.throwException(cause);
            return -1;
        } finally {
            if (free) {
                buffer.release();
            }
        }
    }

    @Override
    protected boolean doWriteMessage(Object msg, ChannelOutboundBuffer in) throws Exception {
        SctpMessage packet = (SctpMessage) msg;
        ByteBuf data = packet.content();
        int dataLen = data.readableBytes();
        if (dataLen == 0) {
            return true;
        }

        ByteBufAllocator alloc = alloc();
        boolean needsCopy = data.nioBufferCount() != 1;
        if (!needsCopy) {
            if (!data.isDirect() && alloc.isDirectBufferPooled()) {
                needsCopy = true;
            }
        }
        ByteBuffer nioData;
        if (!needsCopy) {
            nioData = data.nioBuffer();
        } else {
            data = alloc.directBuffer(dataLen).writeBytes(data);
            nioData = data.nioBuffer();
        }
        final MessageInfo mi = MessageInfo.createOutgoing(association(), null, packet.streamIdentifier());
        mi.payloadProtocolID(packet.protocolIdentifier());
        mi.streamNumber(packet.streamIdentifier());
        mi.unordered(packet.isUnordered());

        final int writtenBytes = javaChannel().send(nioData, mi);
        return writtenBytes > 0;
    }

    @Override
    protected final Object filterOutboundMessage(Object msg) throws Exception {
        if (msg instanceof SctpMessage) {
            SctpMessage m = (SctpMessage) msg;
            ByteBuf buf = m.content();
            if (buf.isDirect() && buf.nioBufferCount() == 1) {
                return m;
            }

            return new SctpMessage(m.protocolIdentifier(), m.streamIdentifier(), m.isUnordered(),
                    newDirectBuffer(m, buf));
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg)
                + " (expected: " + StringUtil.simpleClassName(SctpMessage.class));
    }

    @Override
    public ChannelFuture bindAddress(InetAddress localAddress) {
        return bindAddress(localAddress, newPromise());
    }

    @Override
    public ChannelFuture bindAddress(final InetAddress localAddress, final ChannelPromise promise) {
        if (true) {
            promise.setSuccess();
            return promise;
        }
        if (eventLoop().inEventLoop()) {
            try {
                javaChannel().bindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    bindAddress(localAddress, promise);
                }
            });
        }
        return promise;
    }

    @Override
    public ChannelFuture unbindAddress(InetAddress localAddress) {
        return unbindAddress(localAddress, newPromise());
    }

    @Override
    public ChannelFuture unbindAddress(final InetAddress localAddress, final ChannelPromise promise) {
        if (eventLoop().inEventLoop()) {
            try {
                javaChannel().unbindAddress(localAddress);
                promise.setSuccess();
            } catch (Throwable t) {
                promise.setFailure(t);
            }
        } else {
            eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    unbindAddress(localAddress, promise);
                }
            });
        }
        return promise;
    }

    private final class NioSctpChannelConfig extends DefaultSctpMultiChannelConfig {

        private NioSctpChannelConfig(NioSctpMultiChannel channel, SctpMultiChannel javaChannel) {
            super(channel, javaChannel);
        }

        @Override
        protected void autoReadCleared() {
            setReadPending(false);
        }
    }

    public class DefaultSctpMultiChannelConfig extends DefaultChannelConfig implements SctpChannelConfig {

        private final SctpMultiChannel javaChannel;

        public DefaultSctpMultiChannelConfig(io.netty.channel.sctp.SctpChannel channel, SctpMultiChannel javaChannel) {
            super(channel);
            if (javaChannel == null) {
                throw new NullPointerException("javaChannel");
            }
            this.javaChannel = javaChannel;

            // Enable TCP_NODELAY by default if possible.
            if (PlatformDependent.canEnableTcpNoDelayByDefault()) {
                try {
                    setSctpNoDelay(true);
                } catch (Exception e) {
                    // Ignore.
                }
            }
        }

        @Override
        public Map<ChannelOption<?>, Object> getOptions() {
            return getOptions(
                    super.getOptions(),
                    SO_RCVBUF, SO_SNDBUF, SCTP_NODELAY, SCTP_INIT_MAXSTREAMS);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getOption(ChannelOption<T> option) {
            if (option == SO_RCVBUF) {
                return (T) Integer.valueOf(getReceiveBufferSize());
            }
            if (option == SO_SNDBUF) {
                return (T) Integer.valueOf(getSendBufferSize());
            }
            if (option == SCTP_NODELAY) {
                return (T) Boolean.valueOf(isSctpNoDelay());
            }
            return super.getOption(option);
        }

        @Override
        public <T> boolean setOption(ChannelOption<T> option, T value) {
            validate(option, value);
            try {
                if (option == SO_RCVBUF) {
                    setReceiveBufferSize((Integer) value);
                } else if (option == SO_SNDBUF) {
                    setSendBufferSize((Integer) value);
                } else if (option == SCTP_NODELAY) {
                    setSctpNoDelay((Boolean) value);
                } else if (option == SCTP_INIT_MAXSTREAMS) {
                    setInitMaxStreams((SctpStandardSocketOptions.InitMaxStreams) value);
                } else {
                    return super.setOption(option, value);
                }
            } catch (NotYetBoundException ex) {
                //do nothing
            }

            return true;
        }

        @Override
        public boolean isSctpNoDelay() {
            try {
                for (Association a : javaChannel.associations()) {
                    return javaChannel.getOption(SctpStandardSocketOptions.SCTP_NODELAY, a);
                }
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return false;
        }

        @Override
        public SctpChannelConfig setSctpNoDelay(boolean sctpNoDelay) {
            try {
                for (Association a : javaChannel.associations()) {
                    javaChannel.setOption(SctpStandardSocketOptions.SCTP_NODELAY, sctpNoDelay, a);
                }
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public int getSendBufferSize() {
            try {
                for (Association a : javaChannel.associations()) {
                    return javaChannel.getOption(SctpStandardSocketOptions.SO_SNDBUF, a);
                }
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return 0;
        }

        @Override
        public SctpChannelConfig setSendBufferSize(int sendBufferSize) {
            try {
                for (Association a : javaChannel.associations()) {
                    javaChannel.setOption(SctpStandardSocketOptions.SO_SNDBUF, sendBufferSize, a);
                }
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public int getReceiveBufferSize() {
            try {
                for (Association a : javaChannel.associations()) {
                    return javaChannel.getOption(SctpStandardSocketOptions.SO_RCVBUF, a);
                }
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return 0;
        }

        @Override
        public SctpChannelConfig setReceiveBufferSize(int receiveBufferSize) {
            try {
                for (Association a : javaChannel.associations()) {
                    javaChannel.setOption(SctpStandardSocketOptions.SO_RCVBUF, receiveBufferSize, a);
                }
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public SctpStandardSocketOptions.InitMaxStreams getInitMaxStreams() {
            try {
                for (Association a : javaChannel.associations()) {
                    return javaChannel.getOption(SctpStandardSocketOptions.SCTP_INIT_MAXSTREAMS, a);
                }
            } catch (IOException e) {
                throw new ChannelException(e);
            } catch (NotYetBoundException ex) {
                
            }
            return null;
        }

        @Override
        public SctpChannelConfig setInitMaxStreams(SctpStandardSocketOptions.InitMaxStreams initMaxStreams) {
            try {
                for (Association a : javaChannel.associations()) {
                    javaChannel.setOption(SctpStandardSocketOptions.SCTP_INIT_MAXSTREAMS, initMaxStreams, a);
                }
            } catch (IOException e) {
                throw new ChannelException(e);
            }
            return this;
        }

        @Override
        public SctpChannelConfig setConnectTimeoutMillis(int connectTimeoutMillis) {
            super.setConnectTimeoutMillis(connectTimeoutMillis);
            return this;
        }

        @Override
        @Deprecated
        public SctpChannelConfig setMaxMessagesPerRead(int maxMessagesPerRead) {
            super.setMaxMessagesPerRead(maxMessagesPerRead);
            return this;
        }

        @Override
        public SctpChannelConfig setWriteSpinCount(int writeSpinCount) {
            super.setWriteSpinCount(writeSpinCount);
            return this;
        }

        @Override
        public SctpChannelConfig setAllocator(ByteBufAllocator allocator) {
            super.setAllocator(allocator);
            return this;
        }

        @Override
        public SctpChannelConfig setRecvByteBufAllocator(RecvByteBufAllocator allocator) {
            super.setRecvByteBufAllocator(allocator);
            return this;
        }

        @Override
        public SctpChannelConfig setAutoRead(boolean autoRead) {
            super.setAutoRead(autoRead);
            return this;
        }

        @Override
        public SctpChannelConfig setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
            super.setWriteBufferHighWaterMark(writeBufferHighWaterMark);
            return this;
        }

        @Override
        public SctpChannelConfig setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
            super.setWriteBufferLowWaterMark(writeBufferLowWaterMark);
            return this;
        }

        @Override
        public SctpChannelConfig setMessageSizeEstimator(MessageSizeEstimator estimator) {
            super.setMessageSizeEstimator(estimator);
            return this;
        }
    }

}
