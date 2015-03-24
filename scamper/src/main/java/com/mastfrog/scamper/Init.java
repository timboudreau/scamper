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
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.sctp.SctpChannel;

/**
 * Just initializes the channel, getting the adapter from the provider so that,
 * if it's to be created on every request, a new one is created by Guice.
 *
 * @author Tim Boudreau
 */
final class Init extends ChannelInitializer<SctpChannel> {

    private final Provider<ChannelHandlerAdapter> handler;
    private final Provider<ChannelHandlerAdapter> processor;
    private final Provider<InboundSctpMessageToByteBufDecoder> sctpMessageToBytes;
    private final Provider<InboundMessageProcessor> proc;

    @Inject
    public Init(@Named("dispatcher") Provider<ChannelHandlerAdapter> handler, @Named("processor") Provider<ChannelHandlerAdapter> processor, Provider<InboundSctpMessageToByteBufDecoder> inbound, Provider<InboundMessageProcessor> proc) {
        this.handler = handler;
        this.processor = processor;
        sctpMessageToBytes = inbound;
        this.proc = proc;
    }

    @Override
    protected void initChannel(SctpChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(sctpMessageToBytes.get());
        pipeline.addLast(handler.get());
        pipeline.addLast(processor.get());
        pipeline.addLast(proc.get());
    }
    
    ChannelInitializer<SctpChannel> withAddress(final Address addr) {
        return new ChannelInitializer<SctpChannel>() {

            @Override
            protected void initChannel(SctpChannel ch) throws Exception {
                ch.bindAddress(addr.toSocketAddress().getAddress());
                for (Address secondary : addr) {
                    System.out.println("Bind secondary " + secondary);
                    ch.bindAddress(secondary.toSocketAddress().getAddress());
                }
                Init.this.initChannel(ch);
            }
        };
    }
}
