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
 * An SCTP client which will send and receive SCTP messages.
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

    /**
     * Start the client, returning a ChannelFuture which can be waited on to
     * keep the client running (the returned future is the client SCTP socket's
     * channel's <code>closeFuture()</code> - call its <code>sync()</code>
     * method to block the current thread until the connection is closed.
     *
     * @return The close future for this client's connection
     * @throws InterruptedException if the connect process is interrupted
     */
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
