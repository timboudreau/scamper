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
import com.mastfrog.util.thread.AtomicRoundRobin;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.HashMap;
import java.util.Map;
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

    @Inject
    Associations(final ChannelConfigurer config) {
        this.config = config;
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
            if (future != null) {
                logger.log(Level.FINER, "Reuse connection {0}:{1}", new Object[]{address.host, address.port});
                return future;
            }
            logger.log(Level.FINER, "Open connection {0}:{1}", new Object[]{address.host, address.port});
            Bootstrap bootstrap = new Bootstrap();
            config.init(bootstrap);
            //need sync here?
            ChannelFuture result = bootstrap.connect(address.host, address.port);
            future = result;
            result.addListener(this);
            return result;
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
            channel.closeFuture().addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    logger.log(Level.FINER, "Closed connection {0}:{1}", new Object[]{address.host, address.port});
                    synchronized (Associations.this) {
                        if (Associations.this.associations.get(address) == Asso.this) {
                            Associations.this.associations.remove(address);
                        }
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
}
