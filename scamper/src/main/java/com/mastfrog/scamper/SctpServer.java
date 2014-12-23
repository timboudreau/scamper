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
import com.google.inject.name.Named;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Echoes back any received data from a SCTP client.
 */
public final class SctpServer {

    private final int port;
    private final ChannelConfigurer config;

    @Inject
    SctpServer(@Named("port") int port, Provider<ChannelHandlerAdapter> handler, ChannelConfigurer config) {
        this.port = port;
        this.config = config;
    }

    /**
     * Start the server, returning a ChannelFuture which can be waited on to
     * keep the server running (the returned future is the server socket's
     * channel's <code>closeFuture()</code> - call its <code>sync()</code>
     * method to block the current thread until the connection is closed.
     *
     * @return The close future for this server's SCTP server socket
     * @throws InterruptedException
     */
    public ChannelFuture start() throws InterruptedException {
        // Configure the server.
        ServerBootstrap b = new ServerBootstrap();
        config.init(b);
        b.handler(new LoggingHandler(LogLevel.INFO));

        // Start the server.
        ChannelFuture f = b.bind(port).sync();
        return f.channel().closeFuture();
    }
}
