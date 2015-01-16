Scamper
=======

A toolkit to make it easy to write Java servers and clients that use 
[SCTP](http://en.wikipedia.org/wiki/Stream_Control_Transmission_Protocol)
instead of TCP as their network
transport, using [Netty](http://netty.io) under the hood.

An example chat application can be [found here](https://github.com/timboudreau/scamper-chat).

Servers and clients can register `MessageType`s and `MessageHandler`s that
receive messages of those types.  Each message sent or received by this
library has a 3-byte header identifying the message type, which is used to
route them to the handler you provide for that message type.

The remaining payload of a message is up to you.  You define message types 
and write handlers that receive those messages, or use a `Sender` to send 
them elsewhere.

By default message payloads
are encoded using [BSON](http://en.wikipedia.org/wiki/BSON) using 
[BSON4Jackson](https://github.com/michel-kraemer/bson4jackson).  So sending
messages is as easy as passing a Java object to a `Sender`.

If you want to write your own payloads, simply make the payload a
[ByteBuf](http://netty.io/5.0/api/io/netty/buffer/ByteBuf.html) and
no encoding or decoding will be done.

For debugging, BSON can be turned off and JSON will be used instead.

Javadoc is [available here](http://timboudreau.com/builds/job/scamper/lastSuccessfulBuild/artifact/scamper/target/apidocs/index.html)
and the library is available via Maven [as described here](http://timboudreau.com/builds/):

```xml
    <dependency>
        <groupId>com.mastfrog</groupId>
        <artifactId>scamper</artifactId>
        <version>1.3-dev</version>
    </dependency>
```

What It Does
------------

Allows you to create clients and servers that can pass messages very simply.
At this point the benefits of SCTP are small (multi-homing is TBD), but one
aspect can be seen in that, if you run the date-demo project, you can stop
and restart the server while the client is running, without the client
either failing or needing to do anything to reconnect.

SCTP is message-oriented, like UDP, as opposed to stream-oriented like TCP,
and has the benefit that messages do not block each other, and multiple messages
can be on the wire on the same connection at the same time.  Strict order is
optional.

Requirements
------------

On Linux, you need `lksctp-tools` installed, at least with JDK 8.  If you see
an error about not being able to load a native library, that's the problem.
In other situations, you may need to make sure SCTP support is compiled into
your OS kernel.

If you use the BSON support (i.e. you want to pass POJOs as messages), those
classes will need to be serializable/deserializable by Jackson.


Writing A Server
----------------

You need to code two things:

 * A `MessageType`, which simply defines a pair of bytes at the head of a
message to mark it as that flavor of message

```java
    static final MessageType WHAT_TIME_IS_IT = new MessageType("dateQuery", 1, 1);
    static final MessageType THE_TIME_IS = new MessageType("dateResponse", 1, 2);
```

 * A `MessageHandler` which can receive messages, and optionally reply to them
```java
    static class DateQueryHandler extends MessageHandler<DateRecord, Map> {

        DateQueryHandler() {
            super(Map.class);
        }

        @Override
        public Message<DateRecord> onMessage(Message<Map> data, ChannelHandlerContext ctx) {
            DateRecord response = new DateRecord();
            return RESPONSE.newMessage(response);
        }

        public static class DateRecord {
            public long when = System.currentTimeMillis();
        }
    }
```

The builder class `SctpServerAndClientBuilder` makes it simple to bind these and
create a server:

```java
    public static void main(String[] args) throws IOException, InterruptedException {
        Control<SctpServer> control = new SctpServerAndClientBuilder("date-demo")
                .onPort(8007)
                .withWorkerThreads(3)
                .bind(WHAT_TIME_IS_IT, DateQueryHandler.class)
                .bind(THE_TIME_IS, DateResponseHandler.class)
                .buildServer(args);
        SctpServer server = control.get();
        ChannelFuture future = server.start();
        future.sync();
    }
```

What this does:

 * Configure a server that understands our two MessageTypes, and passes handler
classes for both of them
 * Get back a `Control` object which can be used to shut down that server
 * Get the actual server instance
 * Start it, getting back a `ChannelFuture` which will complete when the 
connection is closed
 * Wait forever on that future, blocking the main thread

[Full source code for the server demo](https://github.com/timboudreau/scamper/blob/master/scamper-date-demo/src/main/java/com/mastfrog/scamper/demo/dates/DateDemo.java)


Writing a Client
----------------

`Sender` is a simple class which maintains a set of connections to clients;
you simply call it with a message and the address you want to send it to.  All
you need is a `MessageType` and an object that Jackson can serialize.  Then
you just `send()` the message to an `Address`.

```java
static final MessageType MY_MESSAGE_TYPE = new MessageType("dateQuery", 1, 1);
...
Message<?> message = MY_MESSAGE_TYPE.newMessage(new MyObject());
sender.send(new Address("127.0.0.1", 8007), message);
```

A client for the server above looks like:

```java
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
```

What this does:

 * Configure a Sender with handlers for our message types
 * Loop forever sending a `WHAT_TIME_IS_IT` message every 5 seconds
    * A Message object is simply a wrapper for a message type and a payload
 * You will see the response logged

See the subproject `scamper-date-demo` to build and run this.

[Full source code for the client demo](https://github.com/timboudreau/scamper/blob/master/scamper-date-demo/src/main/java/com/mastfrog/scamper/demo/dates/DateClientDemo.java)

### About SCTP Channels

An SCTP association is like a TCP connection, but may refer to a *list of host/port pairs*
rather than just one - such connections are called multi-homed, and rely on the
underlying network to find the connection in that list which is the shortest distance
from the sender.

Within that association, there are some number of SCTP "channels" available, each
of which is independent of the others.  This allows multiple messages to be on
the wire at once, without one message blocking the other from being sent.

By default, when a connection is created, this library will ask the implementation
how many channels are available, and each new message is sent, round-robin style
on the next available channel.  If that is not the desired behavior (say, the 
caller is expecting a response on the same channel), you can
explicitly pass a channel number to <code>Sender.send()</code>.

On Linux + JDK 8, at the time of this writing, the range of available channels
through the loopback interface is 0-65535.


### About Netty's ChannelFuture

Netty is asynchronous.  That means that network operations are not completed in
the thread they are invoked in, and notifications of their success or failure
is called asynchronously when the socket is flushed or the operation fails.

Calls that perform network operations return a 
<a href="http://netty.io/5.0/api/io/netty/channel/ChannelFuture.html">ChannelFuture</a>
you can listen on to check the status of the operation,
or allow you to pass a 
<a href="http://netty.io/5.0/api/io/netty/channel/ChannelFutureListener.html">ChannelFutureListener</a>
which will be notified when the operation is completed.

It is important to check 
<a href="http://netty.io/5.0/api/io/netty/util/concurrent/Future.html#cause()">ChannelFuture.cause()</a>
to see that the operation actually succeeded.  If it is null, the operation did
succeed.

You can also implement and bind <a href="ErrorHandler.html">ErrorHandler</a> to
receive uncaught exceptions while processing messages (which can legitimately
happen if, say, a client sends bogus data).  It is always preferable to use a
listener on a specific operation, since that listener is likely to have enough
context to do something more intelligent than just log an error.


### Memory Usage

By default, uses Netty's [PooledByteBufAllocator](http://netty.io/5.0/api/io/netty/buffer/PooledByteBufAllocator.html).
To change this, pass a different allocator to the builder's <code>option()</code> method for
<code>ChannelOption.ALLOCATOR</code>.  This uses a pool of off-heap direct memory storage
with reference-counting to recycle memory - resulting in a server that, once it reaches
a steady state, should allocate little or no more memory at runtime.

If you use Netty's ByteBufs directly, you may need to ensure you call <code>release()</code>
on them when you're done with them, as they are reference-counted.

Status
======

This library is fairly embryonic, but is usable at this point for experimenting
with SCTP.


To-Do
-----

 * Support for multi-homing (right now you could grab the Java SCTP connection after the fact
and add them perhaps - haven't tried it) - should be a first-class feature
   * Implement by extending the Address class to contain multiple `InetSocketAddress`es
 * Expire long-unused connections in `Associations` on a timeout
   * Requires some plumbing to touch a timestamp on each one when it is used
 * Implement compression using a different magic first byte
 * Implement encryption (key-exchange mechanism TBD)

License
=======

[GNU Affero license, version 3](http://www.gnu.org/licenses/agpl-3.0.html)


Why is it called Scamper?
-------------------------

Well, I was shooting for something that incorporated SCTP, but Scamtper is,
unpronouncable, Scampter would get the order wrong, and Scatup didn't 
sound very nice at all.
