package com.mastfrog.scamper.demo.dates;

import com.mastfrog.scamper.Address;
import com.mastfrog.scamper.Control;
import com.mastfrog.scamper.Message;
import com.mastfrog.scamper.Sender;
import com.mastfrog.scamper.SctpServerAndClientBuilder;
import static com.mastfrog.scamper.demo.dates.DateDemo.WHAT_TIME_IS_IT;
import static com.mastfrog.scamper.demo.dates.DateDemo.THE_TIME_IS;
import com.mastfrog.util.collections.MapBuilder;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import java.io.IOException;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class DateClientDemo {

    public static void main(String[] args) throws IOException, InterruptedException {
        Control<Sender> control = new SctpServerAndClientBuilder("date-demo")
                .withHost("127.0.0.1")
                .onPort(8007)
                .bind(WHAT_TIME_IS_IT, DateDemo.DateQueryHandler.class)
                .bind(THE_TIME_IS, DateDemo.DateResponseHandler.class)
                .buildSender(args);

        Sender sender = control.get();

        for (int i = 0;; i++) {
            Address addr = new Address("127.0.0.1", 8007);
            // Just put some random stuff in the inbound message
            Map msg = new MapBuilder().put("id", i).put("client", true).build();
            Message<?> message = DateDemo.WHAT_TIME_IS_IT.newMessage(msg);
            sender.send(addr, message).addListener(new LogResultListener(i));
            Thread.sleep(5000);
        }
    }

    static class LogResultListener implements ChannelFutureListener {

        private final int index;

        public LogResultListener(int index) {
            this.index = index;
        }

        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
            Throwable t = future.cause();
            if (t == null) {
                System.out.println("Request " + index + " succeeded");
            } else {
                System.out.println("FAILED: " + index + " " + t.getClass().getName() + " " + t.getMessage());
                t.printStackTrace();
            }
        }
    }
}
