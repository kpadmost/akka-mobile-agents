package com.kpadmost.connection;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
//import akka.actor.typed.ActorRef;


import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.io.Tcp;
import akka.io.Tcp.Bound;
import akka.io.Tcp.CommandFailed;
import akka.io.Tcp.Connected;
import akka.io.TcpMessage;
import akka.util.ByteString;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Random;


public class TCPServiceAgent extends AbstractActor {
    private LoggingAdapter log;

    private final ActorRef manager;
    private final int port;
    private HashMap<String, ActorRef> clientConnections;


    public static class RenewConnection { // on client supposed reconnection with existing id
        public final String newOldClient;
        public final String newClient;

        public RenewConnection(String newOldClient, String newClient) {
            this.newOldClient = newOldClient;
            this.newClient = newClient;
        }
    }


    public static Props props(ActorRef manager, int port) {
        return Props.create(TCPServiceAgent.class, manager, port);
    }

    public TCPServiceAgent(ActorRef manager, int port) {
        this.manager = manager;
        this.port = port;
        this.log = Logging.getLogger(getContext().getSystem(), this);
        clientConnections = new HashMap<>();
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
                            // register handler
                            final ActorRef handler =  getContext().actorOf(ClientConnectionAgent.create(clientId));
                            clientConnections.put(clientId, handler);
                            getSender().tell(TcpMessage.register(handler), getSelf());
                            // send new id to client
                            JSONObject obj = new JSONObject();
                            obj.put("clid", clientId);
                            getSender().tell(TcpMessage.write(ByteString.fromString(obj.toString() + "\n")), getSelf());
                        })
                .match(RenewConnection.class, msg -> {
                    ActorRef clCon = clientConnections.get(msg.newClient);
                    clientConnections.remove(msg.newClient);
                    log.info("Renewing connection" + msg.newOldClient);
                    clientConnections.put(msg.newOldClient, clCon);

                })
                .build();
    }


    private static String generateRandomString() {
        final int leftLimit = 48; // numeral '0'
        final int rightLimit = 122; // letter 'z'
        final int targetStringLength = 15;
        Random random = new Random();

        return random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
