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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
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
