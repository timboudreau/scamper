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
import com.google.inject.name.Named;
import static com.mastfrog.scamper.ProtocolModule.GUICE_BINDING_SCAMPER_BOSS_THREADS;
import static com.mastfrog.scamper.ProtocolModule.GUICE_BINDING_SCAMPER_CODEC;
import static com.mastfrog.scamper.ProtocolModule.GUICE_BINDING_SCAMPER_WORKER_THREADS;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.sctp.SctpChannelOption;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Initializes new Bootstrap and ServerBoostrap instances with channel options.
 * By default, for servers, sets <code>ChannelOption.SO_BACKLOG</code> to 1000
 * and for clients, sets <code>SctpChannelOption.SCTP_NODELAY</code> to true.
 * <p>
 * If you use {@link SctpServerAndClientBuilder}, it will provide its own
 * implementation of this which will configure things as you have set them in
 * the builder. So this class is only likely to be useful if you're not using
 * that but want to set some options.
 *
 * @author Tim Boudreau
 */
@Singleton
public class ChannelConfigurer {

    protected final EventLoopGroup group;
    protected final EventLoopGroup worker;
    protected final Init init;
    protected final ByteBufAllocator alloc;

    @Inject
    protected ChannelConfigurer(@Named(GUICE_BINDING_SCAMPER_BOSS_THREADS) EventLoopGroup boss, @Named(GUICE_BINDING_SCAMPER_WORKER_THREADS) EventLoopGroup worker, Init init, @Named(GUICE_BINDING_SCAMPER_CODEC) ByteBufAllocator alloc) {
        this.group = boss;
        this.worker = worker;
        this.init = init;
        this.alloc = alloc;
    }

    /**
     * Initialize a server sctp channel
     *
     * @param b The bootstrap
     * @return The bootstrap
     */
    protected ServerBootstrap init(ServerBootstrap b) {
        b = b.group(group, worker)
                .channel(NioSctpServerChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1000)
                .option(ChannelOption.ALLOCATOR, alloc)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(init);
        return b;
    }

    /**
     * Initialize a client sctp channel
     *
     * @param b The bootstrap
     * @return The bootstrap
     */
    protected Bootstrap init(Bootstrap b, Address address) {
        b = b.group(group).channel(NioSctpChannel.class)
                .option(SctpChannelOption.SCTP_NODELAY, true)
                .option(ChannelOption.ALLOCATOR, alloc)
                .handler(new LoggingHandler(LogLevel.INFO))
                .handler(init.withAddress(address));
        return b;
    }
}
