package com.mastfrog.scamper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Maps;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.giulius.ShutdownHookRegistry;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Tim Boudreau
 */
public class MultihomeTest {

    private static final MessageType MSG = new MessageType("x", 1, 2);
    public static final Map<String, List<Thing>> thingsForString = Maps.newConcurrentMap();
    private static final int BASE = 6953;

    public List<Control<SctpServer>> controls = new LinkedList<>();
    public SctpServer[] servers;
    private static final int COUNT = 3;

    private static final Object LOCK = new Object();
    private Control<Sender> senderControl;
    private Sender sender;
    private Address combined;

    @Test
    public void test() throws InterruptedException {
if (true) return;
        assertEquals("localhost", combined.host);
        assertEquals(BASE, combined.port);
        Iterator<Address> subs = combined.iterator();
        assertNotNull(subs);
        assertTrue(subs.hasNext());
        Address sub1 = subs.next();
        assertTrue(subs.hasNext());
        Address sub2 = subs.next();
        assertFalse(subs.hasNext());
        assertEquals(BASE + 1, sub1.port);
        assertEquals(BASE + 2, sub2.port);
        assertFalse(sub1.iterator().hasNext());
        assertFalse(sub2.iterator().hasNext());

        Thing one = new Thing(1);
        Thing xone = new Thing(1);
        assertEquals(one, xone);
        sendAndWait(one);
        List<Thing> all = all();
        assertFalse("Nothing received", all.isEmpty());
        assertEquals(1, all.size());
        assertTrue(all + "", all.contains(one));

        Thing two = new Thing(2);
        sendAndWait(two);
        all = all();
        assertTrue(all + "", all.contains(two));

        // Shut down the primary association's server
        
        servers[0].stop();
        controls.get(0).shutdown();
        Thread.sleep(350);

        Thing three = new Thing(3);
        sendAndWait(three);
        all = all();
        assertTrue(all + " - with primary association down, nothing received", all.contains(three));
        assertFalse("First association is still alive",thingsForString.get("Thing-0").contains(three));

        // Shut down the secondary association's server
        servers[1].stop();
        controls.get(1).shutdown();
        Thread.sleep(350);
        
        Thing four = new Thing(4);
        sendAndWait(four);
        all = all();
        assertTrue(all + " - with primary association down, nothing received", all.contains(four));
        assertFalse("First association is still alive",thingsForString.get("Thing-0").contains(three));
        assertFalse("Second association is still alive",thingsForString.get("Thing-1").contains(three));
        
    }

    private void sendAndWait(Thing t) throws InterruptedException {
        sender.send(combined, MSG.newMessage(t));
        synchronized (LOCK) {
            LOCK.wait(1000);
        }
    }

    private List<Thing> all() {
        List<Thing> result = new LinkedList<>();
        for (List<Thing> t : thingsForString.values()) {
            result.addAll(t);
        }
        return result;
    }

    @After
    public void teardown() throws Exception {
        Exception ex = null;
        for (Control<SctpServer> c : controls) {
            if (c == null) {
                continue;
            }
            try {
                c.getInjector().shutdown();
            } catch (Exception e) {
                ex = e;
            }
        }
        if (senderControl != null) {
            senderControl.getInjector().shutdown();
        }
        if (ex != null) {
            throw ex;
        }
    }

    @Before
    public void setup() throws IOException, InterruptedException {
        servers = new SctpServer[COUNT];
        List<Address> subs = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            final int index = i;
            final String name = "Thing-" + index;
            thingsForString.put(name, Collections.synchronizedList(new LinkedList<>()));
            int port = BASE + i;
            if (i != 0) {
                subs.add(new Address("localhost", port));
            }
            Control<SctpServer> serverControl = new SctpServerAndClientBuilder("test" + i)
                    .onPort(port)
                    .bind(MSG, H.class)
                    .withModule(new AbstractModule() {

                        @Override
                        protected void configure() {
                            bind(String.class).annotatedWith(Names.named("which")).toInstance("Thing-" + index);
                        }
                    }).buildServer();
            controls.add(serverControl);
            servers[i] = serverControl.get();
            servers[i].start();
        }

        combined = new Address("localhost", BASE, subs.toArray(new Address[0]));
        senderControl = new SctpServerAndClientBuilder("testx")
                .buildSender();
        sender = senderControl.get();
        Thread.sleep(200);
    }

    static class H extends MessageHandler<Void, Thing> implements Runnable {

        public final String name;

        @Inject
        H(@Named("which") String name, ShutdownHookRegistry reg) {
            super(Thing.class);
            this.name = name;
            reg.add(this);
        }

        @Override
        public Message<Void> onMessage(Message<Thing> data, ChannelHandlerContext ctx) {
            System.out.println(name + " receives " + data.body);
            List<Thing> l = thingsForString.get(name);
            l.add(data.body);
            synchronized (LOCK) {
                LOCK.notifyAll();
            }
            return null;
        }

        @Override
        public void run() {
            System.out.println("SHUTDOWN " + name);
//            Thread.dumpStack();
        }

    }

    static int ix;

    public static class Thing {

        public final int val;

        @JsonCreator
        public Thing(@JsonProperty("val") int val) {
            this.val = val;
        }

        @Override
        public int hashCode() {
            return val;
        }

        @Override
        public boolean equals(Object o) {
            boolean result = o instanceof Thing && ((Thing) o).val == val;
            return result;
        }

        @Override
        public String toString() {
            return Integer.toString(val);
        }
    }
}
