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

import com.google.inject.Provider;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.sctp.SctpChannel;

/**
 * Just initializes the channel, getting the adapter from the provider
 * so that, if it's to be created on every request, a new one is 
 * created by Guice.
 *
 * @author Tim Boudreau
 */
public class Init extends ChannelInitializer<SctpChannel> {
    private final Provider<ChannelHandlerAdapter> handler;

    public Init(Provider<ChannelHandlerAdapter> handler) {
        this.handler = handler;
    }

    @Override
    protected void initChannel(SctpChannel ch) throws Exception {
        System.out.println("Init channel " + ch.remoteAddress());
        ch.pipeline().addLast(handler.get());
    }

}
