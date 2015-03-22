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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import static com.mastfrog.scamper.ProtocolModule.SETTINGS_KEY_SCTP_PORT;
import com.mastfrog.scamper.SctpServerTest.BsonModule;
import com.mastfrog.scamper.SctpServerTest.JsonModule;
import com.mastfrog.scamper.SctpServerTest.M;
import com.mastfrog.scamper.SctpServerTest.SerializationModule;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiceRunner.class)
@TestWith(iterate = {BsonModule.class, JsonModule.class, SerializationModule.class})
public class SctpServerTest {

    static final MessageType INBOUND = new MessageType("in", 3, 3);
    static final MessageType OUTBOUND = new MessageType("out", 3, 4);

    String longString;
    @Before
    public void before() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append("abcdefghijklmnopqrstuvwxyz1234567890");
        }
        longString = sb.toString();
    }

    @Test
    public void test(Sender sender, CountDownLatch latch, MessageHandlerMapping mapping) throws Throwable {
        assertNotNull(mapping.get(INBOUND));
        assertNotNull(mapping.get(OUTBOUND));

        sender.send(new Address("127.0.0.1", port), INBOUND.newMessage(newThing(1, longString)));
        latch.await(1, TimeUnit.SECONDS);
        errors.maybeThrow();
        assertNotNull(inInstance);
        inInstance.await();
        errors.maybeThrow();
        assertFalse(inInstance.things.isEmpty());
        assertEquals(longString, inInstance.things.iterator().next().what);
        assertEquals(1, inInstance.things.iterator().next().index);
        for (int i = 0; outInstance == null && i < 100; i++) {
            Thread.sleep(20);
        }
        assertNotNull(outInstance);
        System.out.println("Wiat on ");
        outInstance.await();
        System.out.println("done");
        errors.maybeThrow();
        assertFalse(outInstance.strings.isEmpty());
        assertEquals("Received " + longString, outInstance.strings.iterator().next());
    }

    static InHandler inInstance;
    static OutHandler outInstance;

    @Singleton
    static class InHandler extends MessageHandler<OtherThing, Thing> {

        List<Thing> things = Collections.synchronizedList(new LinkedList<>());

        @Inject
        InHandler(CountDownLatch latch) {
            super(Thing.class);
            inInstance = this;
            latch.countDown();
        }

        @Override
        public Message<OtherThing> onMessage(Message<Thing> data, ChannelHandlerContext ctx) {
            System.out.println("in onMessage " + data.body.what + " " + data.body.index);
            assertNotNull(data);
            assertNotNull(data.body);
            Message<OtherThing> result = OUTBOUND.newMessage(new OtherThing("Received " + data.body.what));
            synchronized (this) {
                things.add(data.body);
                notifyAll();
            }
            return result;
        }

        synchronized void await() throws InterruptedException {
            if (!things.isEmpty()) {
                return;
            }
            this.wait(1000);
        }
    }

    @Singleton
    static class OutHandler extends MessageHandler<Void, OtherThing> {

        List<String> strings = Collections.synchronizedList(new LinkedList<>());

        OutHandler() {
            super(OtherThing.class);
            outInstance = this;
        }

        @Override
        public Message<Void> onMessage(Message<OtherThing> data, ChannelHandlerContext ctx) {
            System.out.println("out onMessage " + data.body);
            assertNotNull(data);
            assertNotNull(data.body);
            synchronized (this) {
                strings.add(data.body.data);
                notifyAll();
            }
            return null;
        }

        synchronized void await() throws InterruptedException {
            if (!strings.isEmpty()) {
                return;
            }
            this.wait(1000);
        }
    }

    static int port;

    static class BsonModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new M(DataEncoding.BSON));
        }
    }

    static class JsonModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new M(DataEncoding.JSON));
        }
    }

    static class SerializationModule extends AbstractModule {

        @Override
        protected void configure() {
            install(new M(DataEncoding.JAVA_SERIALIZATION));
        }
    }

    static final class M extends AbstractModule {

        private final DataEncoding enc;

        M(DataEncoding enc) {
            this.enc = enc;
        }

        @Override
        protected void configure() {
            install(new ProtocolModule(1, 1, enc)
                    .bind(INBOUND, InHandler.class)
                    .bind(OUTBOUND, OutHandler.class)
            );
            bind(String.class).annotatedWith(Names.named(SETTINGS_KEY_SCTP_PORT)).toInstance("" + (port = new Random().nextInt(500) + 8000));
            bind(Starter.class).asEagerSingleton();
            bind(ErrorHandler.class).toInstance(errors);
            bind(CountDownLatch.class).toInstance(new CountDownLatch(1));
        }

        static class Starter {

            @Inject
            public void Starter(SctpServer server) throws InterruptedException {
                AtomicReference<ChannelFuture> ref = new AtomicReference<>();
                ChannelFuture fut = server.start(ref);
                assertNotNull(ref.get());
                ref.get().sync();
                Thread.sleep(2000);
            }
        }
    }

    static Thing newThing(int ix, String what) {
        Thing thing = new Thing();
        thing.index = ix;
        thing.what = what;
        return thing;
    }

    static class Thing implements Serializable {

        public String what;
        public int index;

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 79 * hash + Objects.hashCode(this.what);
            hash = 79 * hash + this.index;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Thing other = (Thing) obj;
            if (!Objects.equals(this.what, other.what)) {
                return false;
            }
            return this.index == other.index;
        }

        @Override
        public String toString() {
            return "Thing{" + "what=" + what + ", index=" + index + '}';
        }
    }

    static class OtherThing implements Serializable {

        public final String data;

        @JsonCreator
        public OtherThing(@JsonProperty("data") String data) {
            this.data = data;
        }

        @Override
        public String toString() {
            return "OtherThing{" + "data=" + data + '}';
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 71 * hash + Objects.hashCode(this.data);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final OtherThing other = (OtherThing) obj;
            return Objects.equals(this.data, other.data);
        }
    }

    static final EH errors = new EH();

    static class EH implements ErrorHandler {

        private Throwable t;

        @Override
        public void onError(ChannelHandlerContext ctx, Throwable t) {
            this.t = t;
        }

        public void maybeThrow() throws Throwable {
            if (t != null) {
                Throwable tt = t;
                t = null;
                throw tt;
            }
        }

    }
}
