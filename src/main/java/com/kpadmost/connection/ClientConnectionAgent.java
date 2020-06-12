package com.kpadmost.connection;

import akka.NotUsed;
import akka.actor.Cancellable;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.OutgoingConnection;
import akka.stream.javadsl.*;
import akka.stream.typed.javadsl.ActorFlow;
import akka.util.ByteString;
import com.kpadmost.boardactors.WorkerAgent;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class ClientConnectionAgent extends AbstractBehavior<ClientConnectionAgent.Command> {
    // messages
    public interface Command {}



    // fields
    private int latency;

    private final String clientId;
    private final Tcp.IncomingConnection connection;
    private ActorRef<WorkerAgent.Command> worker;


    private ClientConnectionAgent(ActorContext<Command> context, Tcp.IncomingConnection connection, String clientId) { // TODO: dirty, get
        super(context);
        this.clientId = clientId;
        this.connection = connection;
        listenToConnections();
        context.getLog().info("Agent has been created!");
    }

    public static Behavior<Command> create(Tcp.IncomingConnection connection, String clientId) {
        return Behaviors.setup(context -> new ClientConnectionAgent(context, connection, clientId));
    }



    private Flow<ByteString, ByteString, NotUsed> serverLogic() {
        Source<ByteString, Cancellable> tk = Source.tick(Duration.ofMillis(300), Duration.ofMillis(500), ByteString.fromString(""));
        worker = getContext().spawn(WorkerAgent.create(), String.format("worker-%s",  clientId));
        final Flow<String, String, NotUsed> updateBoardFlow =
                ActorFlow.<String, WorkerAgent.Command, WorkerAgent.BoardUpdated>ask
                        (worker, Duration.ofSeconds(5), (str, replyTo) -> new WorkerAgent.UpdateBoard(5, replyTo))
                        .via(Flow.of(WorkerAgent.BoardUpdated.class).map(updated -> updated.boardState));
        return Flow.of(ByteString.class)
                .merge(tk)
                .map(ByteString::utf8String)
                .via(updateBoardFlow)
                .map(ans -> ans + "\n")
                .map(ByteString::fromString)
                ;
    }



    private void listenToConnections() {
        final ActorSystem system = getContext().getSystem();
        getContext().getLog().info("listening on connection");
        connection.handleWith(serverLogic(), system);


    }

    @Override
    public Receive<Command> createReceive() {
        return null;
    }

}
