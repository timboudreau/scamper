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
