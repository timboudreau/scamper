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
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

/**
 * Sends one message when a connection is open and echoes back any received data
 * to the server over SCTP connection.
 *
 * Simply put, the echo client initiates the ping-pong traffic between the echo
 * client and server by sending the first message to the server.
 */
public final class SctpClient {

    private final String host;
    private final int port;
    private final ChannelConfigurer configurer;

    @Inject
    SctpClient(@Named("host") String host, @Named("port") int port, Provider<ChannelHandlerAdapter> handler, ChannelConfigurer configurer) {
        this.host = host;
        this.port = port;
        this.configurer = configurer;
    }

    public ChannelFuture start() throws InterruptedException {
        // Configure the client.
        Bootstrap b = new Bootstrap();

        configurer.init(b)
                .handler(new LoggingHandler(LogLevel.INFO));

        // Start the client.
        ChannelFuture f = b.connect(host, port).sync();
        // Caller can until the connection is closed.
        return f.channel().closeFuture();
    }
}