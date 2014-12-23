package com.mastfrog.scamper.queries;

import com.mastfrog.scamper.Address;
import com.mastfrog.scamper.Message;
import com.mastfrog.scamper.MessageHandler;
import io.netty.channel.ChannelHandlerContext;

/**
 *
 * @author Tim Boudreau
 */
public class QueryMessageHandler extends MessageHandler<Query, Query> {
    private final Address myAddress;
    
    QueryMessageHandler(Address myAddress) {
        super(Query.class);
        this.myAddress = myAddress;
    }

    @Override
    public Message<Query> onMessage(Message<Query> data, ChannelHandlerContext ctx) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
