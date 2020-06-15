package com.kpadmost.connection;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
//import akka.actor.typed.ActorRef;
import akka.actor.typed.PreRestart;
import akka.actor.typed.javadsl.*;


import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.io.Tcp;
import akka.io.Tcp.Bound;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;
import akka.io.Tcp.ConnectionClosed;
import akka.io.Tcp.Received;
import akka.io.TcpMessage;
import akka.util.ByteString;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Random;


public class ConnectionAgent extends AbstractActor {
    public interface Command {}
    LoggingAdapter log;

    final ActorRef manager;
    final int port;

    public static Props props(ActorRef manager, int port) {
        return Props.create(ConnectionAgent.class, manager, port);
    }

    public ConnectionAgent(ActorRef manager, int port) {
        this.manager = manager;
        this.port = port;
        this.log = Logging.getLogger(getContext().getSystem(), this);
        listen();
    }

    @Override
    public void preStart() throws Exception {
        final ActorRef tcpManager = Tcp.get(getContext().getSystem()).manager();
        tcpManager.tell(TcpMessage.bind(getSelf(), new InetSocketAddress("0.0.0.0", port), 100), getSelf());
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(
                        Bound.class,
                        msg -> {
                            log.info("Bound!" + msg.localAddress());

                            manager.tell(msg, getSelf());
                        })
                .match(
                        CommandFailed.class,
                        msg -> {
                            log.info("Failed");
                            getContext().stop(getSelf());
                        })
                .match(
                        Connected.class,
                        conn -> {
                            manager.tell(conn, getSelf());
                            String clientId = generateRandomString();
                            log.info("Connected!" + conn.remoteAddress() + " clid " + clientId);
                            final ActorRef
                                    handler =
                                    getContext().actorOf(ClientConnectionAgent.create(clientId, 50));
                            getSender().tell(TcpMessage.register(handler), getSelf());
                            JSONObject obj = new JSONObject();
                            obj.put("clid", clientId);
                            getSender().tell(TcpMessage.write(ByteString.fromString(obj.toString() + "\n")), getSelf());
                        })
                .build();
    }


    private void listen() {

    }


    private static String generateRandomString() {
        int leftLimit = 48; // numeral '0'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 15;
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
