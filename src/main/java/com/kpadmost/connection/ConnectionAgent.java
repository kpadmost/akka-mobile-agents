package com.kpadmost.connection;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.stream.javadsl.Tcp;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


public class ConnectionAgent extends AbstractBehavior<ConnectionAgent.Command> {
    public interface Command {}

    public static class InitConnectionRequest implements Command {
        public final int requestId;
        final Tcp.IncomingConnection connection;


        public InitConnectionRequest(
                int requestId,
                Tcp.IncomingConnection connection
        ) {
            this.requestId = requestId;
            this.connection = connection;
        }
    }


    public static class ConnectionEstablished {
        public final String clientId;

        public ConnectionEstablished(String clientId) {
            this.clientId = clientId;
        }
    }


    Map<String, ActorRef<ClientConnectionAgent.Command>> workerActors = new HashMap<>();

    public static Behavior<Command> create() {
        return Behaviors.setup(ConnectionAgent::new);
    }

    private ConnectionAgent(ActorContext<Command> context) {
        super(context);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(InitConnectionRequest.class, this::onEstablishConnection)
                .build();

    }

    public ConnectionAgent onEstablishConnection(InitConnectionRequest request) {
        // assign client id
        final String clientId = generateRandomString();
        // create worker, ask him to bind port
        try {
            getContext().getLog().info("Establishing connection: " + clientId);
            ActorRef<ClientConnectionAgent.Command> connWorker =
                getContext()
                        .spawn(ClientConnectionAgent.create(
                                request.connection, clientId), "conn-worker-"+clientId);
            getContext().getLog().info("Established connection: " + clientId);
//            request.replyTo.tell(new ConnectionEstablished(clientId));
        } catch (Exception e) {
            getContext().getLog().error(e.getMessage());
        }
        return this;
    }

    private int getFreePort()  {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (Exception e) {
            getContext().getLog().error(e.getMessage());
        }
        return 0;
    }


    private String generateRandomString() {
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
