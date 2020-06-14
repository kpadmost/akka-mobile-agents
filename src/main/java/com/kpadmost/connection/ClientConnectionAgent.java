package com.kpadmost.connection;

import akka.NotUsed;
import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Adapter;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.stream.javadsl.*;
import akka.stream.typed.javadsl.ActorFlow;
import akka.util.ByteString;
import com.kpadmost.boardactors.WorkerAgent;
import org.json.JSONException;
import org.json.JSONObject;
import scala.util.control.Exception;

import java.net.InetAddress;
import java.time.Duration;

public class ClientConnectionAgent extends AbstractActor {
    // messages
    public interface Command {}



    // fields
    private int latency;

    private final String clientId;
    private final InetAddress connection;
    private ActorRef socketSender = null;
    private akka.actor.typed.ActorRef<WorkerAgent.Command> worker;


    public static Props create(InetAddress connection, String clientId, int latency) {
        return Props.create(ClientConnectionAgent.class, connection, clientId, latency);
    }

    public ClientConnectionAgent(InetAddress connection, String clientId, int latency) { // TODO: dirty, get
        this.clientId = clientId;
        this.connection = connection;
        this.latency = latency;
    }


    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(
                        Tcp.Received.class,
                        msg -> {
                            final ByteString data = msg.data();
                            System.out.println(data.utf8String());
                            if(socketSender == null)
                                socketSender = getSender();
                            else
                                socketSender.tell(TcpMessage.write(data), getSelf());
                            worker =  Adapter.spawn(getContext(), WorkerAgent.create(), "worker-" + clientId);
                            getContext().getSystem().scheduler().scheduleAtFixedRate(
                                    Duration.ofSeconds(1),
                            Duration.ofMillis(50), () -> {
                                        worker.tell(new WorkerAgent.UpdateBoard(50, getSelf()));
                                    },
                            getContext().getDispatcher());
                        })
                .match(
                        Tcp.ConnectionClosed.class,
                        msg -> {
                            getContext().getSystem().log().info("Stop actor of " + clientId + "!");
                            getContext().stop(getSelf());
                        })
                .match(
                        WorkerAgent.BoardUpdated.class,
                        msg -> {
                            String upd = msg.boardState;

                            socketSender.tell(TcpMessage.write(ByteString.fromString(upd + "\n")), getSelf());

                })
                .build();
    }


    private void parseConnection(String msg) {

    }

}
