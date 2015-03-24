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
import static com.mastfrog.scamper.ProtocolModule.SETTINGS_KEY_SCTP_PORT;
import com.mastfrog.settings.Settings;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An SCTP client which will send and receive SCTP messages.
 */
public final class SctpClient {

    private final Address address;
    private final ChannelConfigurer configurer;
    private static final Logger logger = Logger.getLogger(SctpClient.class.getName());
    
    public static final String SETTINGS_KEY_SECONDARY_ADDRESSES = "sctp.peers";

    @Inject
    SctpClient(@Named("host") String host, @Named(SETTINGS_KEY_SCTP_PORT) int port, Provider<ChannelHandlerAdapter> handler, ChannelConfigurer configurer, Settings settings) {
        this.configurer = configurer;
        String peers = settings.getString(SETTINGS_KEY_SECONDARY_ADDRESSES);
        if (peers != null) {
            List<Address> secondaries = new ArrayList<>();
            for (String a : peers.split(",")) {
                a = a.trim();
                secondaries.add(new Address(a));
            }
            this.address = new Address(host, port, secondaries.toArray(new Address[secondaries.size()]));
        } else {
            this.address = new Address(host, port);
        }
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

        configurer.init(b, address)
                .handler(new LoggingHandler(LogLevel.INFO));

        logger.log(Level.INFO, "Start for {0} on {1}", new Object[]{address.host, address.port});
        // Start the client.
        ChannelFuture f = b.connect(address.host, address.port);
        if (logger.isLoggable(Level.FINE)) {
            f.addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.cause() != null) {
                        logger.log(Level.SEVERE, "Connect failed to " + address, future.cause());
                        return;
                    }
                    logger.log(Level.FINE, "Connected to {0}:{1}", new Object[]{address.host, address.port});
                }
            });
        }
        f.sync();
        // Caller can until the connection is closed.
        return f.channel().closeFuture();
    }
}
