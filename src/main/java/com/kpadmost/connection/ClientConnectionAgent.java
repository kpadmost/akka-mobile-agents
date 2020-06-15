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
import javafx.scene.control.TextFormatter;
import org.json.JSONException;
import org.json.JSONObject;
import scala.util.control.Exception;

import java.net.InetAddress;
import java.time.Duration;

public class ClientConnectionAgent extends AbstractActor {
    // messages
    public interface Command {}


    public static class ChangeLatency {
        public final int latency;

        public ChangeLatency(int latency) {
            this.latency = latency;
        }
    }

    public static class InitEmission {

    }

    static class Reconnect {
        final String whereTo;

        public Reconnect(String whereTo) {
            this.whereTo = whereTo;
        }
    }


    // fields
    private int latency;

    private final String clientId;
    private ActorRef socketSender = null;
    private akka.actor.typed.ActorRef<WorkerAgent.Command> worker;
    private Cancellable updateEmission = null;

    public static Props create(String clientId, int latency) {
        return Props.create(ClientConnectionAgent.class, clientId, latency);
    }

    public ClientConnectionAgent(String clientId, int latency) { // TODO: dirty, get
        this.clientId = clientId;
        this.latency = latency;
    }


    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(
                        Tcp.Received.class,
                        msg -> {
                            if(socketSender == null)
                                socketSender = getSender();

                            final ByteString data = msg.data();
                            System.out.println(data.utf8String());
                            parseConnectionData(data.utf8String());

//                            socketSender.tell(TcpMessage.write(data), getSelf());

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
                .match(ChangeLatency.class, msg -> {
                    getContext().getSystem().log().info("changing latency on " + clientId);
                    latency = msg.latency;
                    if(updateEmission != null) {
                        updateEmission.cancel(); // BUGFIX - race condition?

                        updateEmission = getContext().getSystem().scheduler().scheduleAtFixedRate(
                                Duration.ofSeconds(1),
                                Duration.ofMillis(latency), () -> {
                                    worker.tell(new WorkerAgent.UpdateBoard(50, getSelf()));
                                },
                                getContext().getDispatcher());
                    }

                })
                .match(InitEmission.class, msg -> {
                    getContext().getSystem().log().info("init emission on " + clientId);
                    if(worker == null) {
                        worker = Adapter.spawn(getContext(), WorkerAgent.create(), "worker-" + clientId);
                        updateEmission = getContext().getSystem().scheduler().scheduleAtFixedRate(
                                Duration.ofSeconds(1),
                                Duration.ofMillis(latency), () -> {
                                    worker.tell(new WorkerAgent.UpdateBoard(50, getSelf()));
                                },
                                getContext().getDispatcher());
                    }
                })
                .match(Reconnect.class, mst -> {
                    getContext().getSystem().log().info("Reconnecting " + clientId + "! to " + mst.whereTo);
                    getContext().getSystem().log().info("Stop actor of " + clientId + "!");
                    getContext().stop(getSelf());
                })
                .build();
    }


    private void parseConnectionData(String msg) {
        try {
            JSONObject js = new JSONObject(msg);
            String command = js.getString("command"); // TODO add enum
            if(command.equals("change_latency")) {
                int newLatency = js.getInt("latency");
                onChangeLatency(newLatency);
            } else if(command.equals("init")) {
                latency = js.getInt("latency");
                onInit();
            } else if(command.equals("reconnect")) {
                String whereTo = js.getString("where");
                onReconnect(whereTo);
            }
        } catch (JSONException e) {
            getContext().getSystem().log().error("FAiled parse message! " + e.getMessage());
        }
    }

    private void onReconnect(String where) {
        getSelf().tell(new Reconnect(where), getSelf());
    }

    private void onChangeLatency(int newLatency) {
        getSelf().tell(new ChangeLatency(newLatency), getSelf());
    }

    private void onInit() {
        getSelf().tell(new InitEmission(), getSelf());
    }

}
