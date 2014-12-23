package com.mastfrog.scamper.demo.dates;

import com.mastfrog.scamper.SctpServer;
import com.mastfrog.scamper.Control;
import com.mastfrog.scamper.Message;
import com.mastfrog.scamper.MessageHandler;
import com.mastfrog.scamper.MessageType;
import com.mastfrog.scamper.SctpServerAndClientBuilder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.util.Date;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class DateDemo {

    public static final MessageType WHAT_TIME_IS_IT = new MessageType("dateQuery", 1, 1);
    public static final MessageType THE_TIME_IS = new MessageType("dateResponse", 1, 2);

    public static void main(String[] args) throws IOException, InterruptedException {
        Control<SctpServer> control = new SctpServerAndClientBuilder("datedemo")
                .onPort(8007)
                .withWorkerThreads(3)
                .bind(WHAT_TIME_IS_IT, DateQueryHandler.class)
                .bind(THE_TIME_IS, DateResponseHandler.class)
                .buildServer(args);
        SctpServer server = control.get();
        ChannelFuture future = server.start();
        future.sync();
    }

    static class DateQueryHandler extends MessageHandler<DateRecord, Map> {

        DateQueryHandler() {
            super(Map.class);
        }

        @Override
        public Message<DateRecord> onMessage(Message<Map> data, ChannelHandlerContext ctx) {
            DateRecord response = new DateRecord();
            System.out.println("Send response for query " + data);
            return THE_TIME_IS.newMessage(response);
        }
    }

    static class DateResponseHandler extends MessageHandler<Map, DateRecord> {

        DateResponseHandler() {
            super(DateRecord.class);
        }

        int ix;

        @Override
        public Message<Map> onMessage(Message<DateRecord> data, ChannelHandlerContext ctx) {
            System.out.println("RECEIVE " + new Date(data.body.when) + " from " + ctx.channel().remoteAddress() + " at " + new Date());
            return null;
        }
    }

    public static class DateRecord {

        public long when = System.currentTimeMillis();
    }
}
