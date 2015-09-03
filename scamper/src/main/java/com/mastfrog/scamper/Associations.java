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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.thread.AtomicRoundRobin;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.sctp.SctpChannel;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Maintains a mapping of addresses and open channels, and manages a rotating
 * list of SCTP "channels" (multiplexed payloads) so that messages do not block
 * each other.
 *
 * @author Tim Boudreau
 */
@Singleton
final class Associations {

    private final ChannelConfigurer config;
    private final Map<Address, Asso> associations = new HashMap<>();
    private static final AttributeKey<AtomicRoundRobin> NEXT_IN_STREAM
            = AttributeKey.valueOf(Associations.class, "instream");
    private static final AttributeKey<AtomicRoundRobin> NEXT_OUT_STREAM
            = AttributeKey.valueOf(Associations.class, "outstream");
    private static final Logger logger = Logger.getLogger(Associations.class.getName());
    private final ErrorHandler handler;

    @Inject
    Associations(final ChannelConfigurer config, ErrorHandler handler) {
        this.config = config;
        this.handler = handler;
    }

    public ChannelFuture connect(Address address) {
        Asso result;
        synchronized (this) {
            result = associations.get(address);
            if (result == null) {
                result = new Asso(address);
                associations.put(address, result);
            }
        }
        return result.connect();
    }

    void ensureRegistered(ChannelHandlerContext ctx) {
        Address addr = new Address((InetSocketAddress) ctx.channel().remoteAddress());
        Asso asso = associations.get(addr);
        if (asso != null) {
            return;
        }
        synchronized (this) {
            asso = associations.get(addr);
            if (asso == null) {
                asso = new Asso(addr, (NioSctpChannel) ctx.channel());
                asso.future = ctx.channel().newSucceededFuture();
                ctx.channel().closeFuture().addListener(asso);
                associations.put(addr, asso);
            }
        }
    }

    public void register(Channel channel) {
        getForKey(NEXT_OUT_STREAM, channel);
    }

    public boolean disconnectIfConnected(Address a) {
        Asso asso;
        synchronized (this) {
            asso = associations.get(a);
        }
        boolean disconnected = false;
        if (asso != null) {
            ChannelFuture fut = asso.future;
            if (fut != null) {
                Channel ch = fut.channel();
                if (ch != null && ch.isOpen()) {
                    ch.close();
                    disconnected = true;
                }
            }
        }
        return disconnected;
    }

    private int getForKey(AttributeKey<AtomicRoundRobin> key, Channel channel) {
        Attribute<AtomicRoundRobin> attr = channel.attr(key);
        AtomicRoundRobin r = attr.get();
        if (r == null && channel instanceof NioSctpChannel) {
            synchronized (this) {
                NioSctpChannel ch = (NioSctpChannel) channel;
                Address address = new Address((InetSocketAddress) ch.remoteAddress());
                Asso asso = new Asso(address, ch);
                associations.put(address, asso);
                attr = channel.attr(key);
                r = attr.get();
            }
        }
        return r == null ? 0 : r.get();
    }

    public int nextInStream(Channel channel) {
        return getForKey(NEXT_IN_STREAM, channel);
    }

    public int nextOutStream(Channel channel) {
        return getForKey(NEXT_OUT_STREAM, channel);
    }

    public synchronized int nextInStream(Address addr) {
        Asso asso = associations.get(addr);
        return asso == null ? 0 : asso.nextInStream();
    }

    public synchronized int nextOutStream(Address addr) {
        Asso asso = associations.get(addr);
        return asso == null ? 0 : asso.nextOutStream();
    }

    private final class Asso implements ChannelFutureListener {

        private final Address address;
        private ChannelFuture future;
        private AtomicRoundRobin inStreams;
        private AtomicRoundRobin outStreams;

        Asso(Address address) {
            this.address = address;
        }

        Asso(Address address, NioSctpChannel channel) {
            this(address);
            onChannelAcquired(channel);
        }

        public synchronized int nextInStream() {
            return inStreams == null ? 0 : inStreams.next();
        }

        public synchronized int nextOutStream() {
            return outStreams == null ? 0 : outStreams.next();
        }

        public synchronized ChannelFuture connect() {
            try {
                if (future instanceof FailedFuture) { // allow retry
                    future = null;
                }
                if (future != null /* && future.channel().isOpen()*/) {
                    logger.log(Level.FINER, "Reuse connection {0}:{1}", new Object[]{address.host, address.port});
                    return future;
                }
                ChannelFuture result;
                logger.log(Level.FINER, "Open connection {0}:{1}", new Object[]{address.host, address.port});
                Bootstrap bootstrap = new Bootstrap();
                config.init(bootstrap, address);

                result = future = Associations.bindAddresses(address, bootstrap);
                result.addListener(this);
            } catch (Exception e) {
                e.printStackTrace();
                handler.onError(null, e);
                return future = new FailedFuture(e);
            }
            return future;
        }

        public void close() {
            ChannelFuture f;
            synchronized (this) {
                f = future;
            }
            if (f != null) {
                f.cancel(true);
                f.channel().close();
                synchronized (Associations.this) {
                    Asso c = associations.get(address);
                    if (c == this) {
                        associations.remove(address);
                    }
                }
            }
        }

        void onChannelAcquired(NioSctpChannel channel) {
            synchronized (this) {
                inStreams = new AtomicRoundRobin(channel.config().getInitMaxStreams().maxInStreams());
                outStreams = new AtomicRoundRobin(channel.config().getInitMaxStreams().maxOutStreams());
                channel.attr(NEXT_IN_STREAM).set(inStreams);
                channel.attr(NEXT_OUT_STREAM).set(outStreams);
            }
            System.err.println("LOCAL ADDRS: " + channel.allLocalAddresses());
            System.err.println("REMOTE ADDRS: " + channel.allRemoteAddresses());
            System.err.println("ASSOC: " + channel.association());
            channel.closeFuture().addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    System.err.println("Connection to " + address + " closed");
//                    if (address.iterator().hasNext()) {
//                        System.err.println("Leaving association in place");
//                        return;
//                    }
                    logger.log(Level.FINER, "Closed connection {0}:{1}", new Object[]{address.host, address.port});
                    synchronized (Associations.this) {
//                        if (Associations.this.associations.get(address) == Asso.this) {
//                            Associations.this.associations.remove(address);
//                        }
                    }
                }
            });
        }

        @Override
        @SuppressWarnings("ThrowableResultIgnored")
        public void operationComplete(ChannelFuture future) throws Exception {
            NioSctpChannel channel;
            if (future.cause() != null) {
                synchronized (Associations.this) {
                    if (associations.get(address) == Asso.this) {
                        associations.remove(address);
                    }
                }
                Throwable c = future.cause();
                while (c.getCause() != null) {
                    c = c.getCause();
                }
                logger.log(Level.FINER, "Failed connecting to " + address, c);
                synchronized (Associations.this) {
                    Asso a = associations.get(address);
                    if (a == Asso.this) {
                        associations.remove(address);
                    }
                }
                return;
            } else {
                logger.log(Level.FINER, "Opened connection {0}:{1}", new Object[]{address.host, address.port});
            }
            synchronized (this) {
                channel = (NioSctpChannel) future.channel();
            }
            try {
                onChannelAcquired(channel);
            } catch (ChannelException ex) {
                logger.log(Level.FINE, "Failed to connect", ex);
                if (ex.getCause() instanceof ClosedChannelException) {
                    synchronized (Associations.this) {
                        Asso a = associations.get(address);
                        if (a == Asso.this) {
                            associations.remove(address);
                        }
                    }
                }
            }
        }
    }

    static class FailedFuture extends DefaultChannelPromise implements Future<Void> {

        public FailedFuture(Throwable t) {
            super(null);
            setFailure(t);
        }

        @Override
        @SuppressWarnings("unchecked")
        public ChannelPromise addListener(GenericFutureListener<? extends Future<? super Void>> listener) {
            GenericFutureListener gf = listener;
            try {
                gf.operationComplete(this);
            } catch (Exception ex) {
                Exceptions.chuck(ex);
            }
            return this;
        }

        @Override
        public ChannelPromise addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners) {
            for (GenericFutureListener<? extends Future<? super Void>> f : listeners) {
                addListeners(f);
            }
            return this;
        }
    }

    static class SettableFuture extends DefaultChannelPromise implements ChannelFuture {

        private Channel channel;

        public SettableFuture() {
            super(null);
        }

        void setChannel(Channel channel) {
            this.channel = channel;
            this.setSuccess();
        }

        @Override
        public Channel channel() {
            return channel;
        }
    }

    static ChannelFuture bindAddresses(final Address address, final Bootstrap bootstrap) throws InterruptedException, UnknownHostException {
        InetSocketAddress local = new InetSocketAddress("localhost", 0);
        ChannelFuture bindFuture = bootstrap.bind(local).sync();
        final NioSctpChannel channel = (NioSctpChannel) bindFuture.channel();

        Set<String> seen = new HashSet<>();
        for (final Address a : address) {
            System.out.println("BIND " + a);
            InetAddress addr = "localhost".equals(a.host) ? InetAddress.getByAddress(new byte[]{127, 0, 0, 1}) : InetAddress.getByName(a.host);
            String canon = addr.toString();
            System.err.println("CANON: " + canon);
            if (!seen.contains(canon)) {
                System.out.println("BIND ADDTL " + addr);
                ChannelFuture cf = channel.bindAddress(addr).sync();
//                ChannelFuture cf = channel.bind(new InetSocketAddress(addr, a.port));
                cf.addListener(new ChannelFutureListener() {

                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            System.out.println("Bind succeeded for " + a);
                        } else {
                            System.err.println("FAILURE BINDING " + a + ": " + future.cause());
                        }
                    }

                });
            }
            seen.add(canon);
        }
        bootstrap.remoteAddress(address.resolve());
        ChannelFuture connectFuture = bindFuture;
        System.err.println("CHANNEL IS " + connectFuture.channel());
        return bootstrap.connect();
    }

    static ChannelFuture xbindAddresses(final Address address, final Bootstrap bootstrap) {
        // c.f. https://groups.google.com/forum/#!searchin/netty/sctp/netty/k5MXiZ_Tx20/fH9zcfsp52AJ
        final SettableFuture result = new SettableFuture();
        int count = address.secondaries.length + 1;
        final List<Address> toConnect = new ArrayList<>(count);

        List<Address> secs = Arrays.asList(address.secondaries);
        System.err.println("SECONDARY ADDRESSES " + secs);

        final AtomicInteger secondaryIndex = new AtomicInteger(0);

        final AtomicReference<SctpChannel> lastSuccessChannel = new AtomicReference<>();

        System.err.println("BindAndConnect " + address);

        class ConnectOne implements ChannelFutureListener {

            private final Address addr;

            final CollectionUtils.AtomicIterator<Address> iter;
            final AtomicInteger connectAttempts;
            final int totalAttempts;

            public ConnectOne(Address addr, CollectionUtils.AtomicIterator<Address> iter, int totalAttempts, AtomicInteger connectAttempts) {
                this.addr = addr;
                this.iter = iter;
                this.totalAttempts = totalAttempts;
                this.connectAttempts = connectAttempts;
            }

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                System.err.println("Connect result " + addr + " success? " + future.isSuccess());
                if (future.cause() != null) {
                    future.cause().printStackTrace();
                }
                SctpChannel ch = (SctpChannel) future.channel();
                if (future.isSuccess()) {
                    lastSuccessChannel.set(ch);
                }
                Address next = iter.getIfHasNext();
                if (next != null) {
                    System.err.println("Next connect is to " + next);
                    ch.connect(new InetSocketAddress(next.host, next.port)).addListener(new ConnectOne(next, iter, totalAttempts, connectAttempts));
                }
                if (connectAttempts.incrementAndGet() == totalAttempts) {
                    SctpChannel forResult = lastSuccessChannel.get();
                    System.err.println("Done with connecting, send success? " + (forResult != null));
                    if (forResult == null) {
                        result.setFailure(new IOException("No channel connected"));
                    } else {
                        result.setChannel(forResult);
                    }
                }
            }

        }

        class BindOne implements ChannelFutureListener {

            final Address addr;

            public BindOne(Address addr) {
                this.addr = addr;
            }

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                System.err.println("Bind one " + addr + " success? " + future.isSuccess());
                if (future.cause() != null) {
                    future.cause().printStackTrace();
                }
                if (future.isSuccess()) {
                    toConnect.add(addr);
                    System.err.println("Add to success " + addr);
                }
                int ix = secondaryIndex.getAndIncrement();
                Address next = null;
                if (ix < address.secondaries.length) {
                    next = address.secondaries[ix];
                }
                SctpChannel ch = (SctpChannel) future.channel();
                if (next == null) {
                    System.err.println("Bind next: " + next);
                    ch.bindAddress(InetAddress.getByName(next.host));
                } else {
                    System.err.println("No next address");
                }
                System.err.println("Iteration " + ix + " secs length " + address.secondaries.length);
                if (ix == address.secondaries.length - 1) {
                    System.err.println("Bound all addresses there are, proceed to connect");
                    CollectionUtils.AtomicIterator<Address> connects = CollectionUtils.synchronizedIterator(toConnect.iterator());
                    Address firstConnect = connects.getIfHasNext();
                    if (firstConnect == null) {
                        result.setFailure(future.cause());
                    } else {
                        System.err.println("Connect " + firstConnect);
                        ch.connect(new InetSocketAddress(firstConnect.host, firstConnect.port)).addListener(new ConnectOne(firstConnect, connects, toConnect.size(), new AtomicInteger()));
                    }
                }
            }
        }
        bootstrap.connect(new InetSocketAddress(address.host, address.port)).addListener(new BindOne(address));
        return result;
    }

    static class BS extends Bootstrap {

        @Override
        public ChannelFuture bind(SocketAddress localAddress) {
            new Exception("Bind " + localAddress).printStackTrace();
            return super.bind(localAddress); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public ChannelFuture bind(InetAddress inetHost, int inetPort) {
            new Exception("Bind " + inetHost + ":" + inetPort).printStackTrace();
            return super.bind(inetHost, inetPort); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public ChannelFuture bind(String inetHost, int inetPort) {
            new Exception("Bind " + inetHost + ":" + inetPort).printStackTrace();
            return super.bind(inetHost, inetPort); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public ChannelFuture bind(int inetPort) {
            new Exception("Bind " + inetPort).printStackTrace();
            return super.bind(inetPort); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
