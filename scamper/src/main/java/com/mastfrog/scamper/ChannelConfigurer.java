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
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.sctp.SctpChannelOption;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 *
 * @author Tim Boudreau
 */
@Singleton
public class ChannelConfigurer {

    protected final EventLoopGroup group;
    protected final EventLoopGroup worker;
    protected final Provider<ChannelHandlerAdapter> handler;

    @Inject
    protected ChannelConfigurer(@Named(value = "boss") EventLoopGroup boss, @Named("worker") EventLoopGroup worker, Provider<ChannelHandlerAdapter> handler) {
        this.group = boss;
        this.worker = worker;
        this.handler = handler;
    }

    protected ServerBootstrap init(ServerBootstrap b) {
        Init init = new Init(handler);
        b = b.group(group, worker)
                .channel(NioSctpServerChannel.class)
                .option(ChannelOption.SO_BACKLOG, 1000)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(init);
        return b;
    }

    protected Bootstrap init(Bootstrap b) {
        Init init = new Init(handler);
        b = b.group(group).channel(NioSctpChannel.class)
                .option(SctpChannelOption.SCTP_NODELAY, true)
                .handler(init);
        return b;
    }

}
