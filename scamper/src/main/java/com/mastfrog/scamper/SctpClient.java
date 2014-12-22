/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
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
