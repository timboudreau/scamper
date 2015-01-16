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
import com.mastfrog.giulius.ShutdownHookRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An SCTP server which will listen on the specified port and decode and pass
 * received messages to the bound MessageHandlers.
 */
public final class SctpServer {

    private final int port;
    private final ChannelConfigurer config;
    private ChannelFuture future;
    private static final Logger logger = Logger.getLogger(SctpServer.class.getName());

    @Inject
    SctpServer(@Named("port") int port, Provider<ChannelHandlerAdapter> handler, ChannelConfigurer config, ShutdownHookRegistry reg) {
        this.port = port;
        this.config = config;
        reg.add(new Runnable() {

            @Override
            public void run() {
                ChannelFuture f = stop();
                if (f != null) {
                    try {
                        f.sync();
                    } catch (InterruptedException ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                }
            }
        });
    }

    /**
     * Start the server, returning a ChannelFuture which can be waited on to
     * keep the server running (the returned future is the server socket's
     * channel's <code>closeFuture()</code> - call its <code>sync()</code>
     * method to block the current thread until the connection is closed.
     *
     * @return The close future for this server's SCTP server socket
     * @throws InterruptedException If the socket binding process is
     * interrupted, say be VM shutdown
     */
    public ChannelFuture start() throws InterruptedException {
        return start(null);
    }

    public ChannelFuture start(AtomicReference<ChannelFuture> connectFutureReceiver) throws InterruptedException {
        // Configure the server.
        ServerBootstrap b = new ServerBootstrap();
        config.init(b);
        b.handler(new LoggingHandler(LogLevel.INFO));

        logger.log(Level.FINE, "Start server on {0}", port);
        // Start the server.
        ChannelFuture f = b.bind(port);
        if (logger.isLoggable(Level.FINE)) {
            f.addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    logger.log(Level.FINE, "Listening for connections on {0}", port);
                }

            });
        }
        f.sync();
        logger.log(Level.FINER, "Thread proceeding", Thread.currentThread());
        // For tests and things that need to delay execution until a connection
        // has been opened
        if (connectFutureReceiver != null) {
            connectFutureReceiver.set(f);
        }
        synchronized (this) {
            return future = f.channel().closeFuture();
        }
    }

    public ChannelFuture stop() {
        ChannelFuture theFuture;
        synchronized (this) {
            theFuture = future;
        }
        if (theFuture != null) {
            theFuture.channel().close();
        }
        return theFuture;
    }
}
