package com.kpadmost.connection;

import akka.actor.*;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.Adapter;
import akka.cluster.sharding.external.ExternalShardAllocation;
import akka.cluster.sharding.external.javadsl.ExternalShardAllocationClient;
import akka.cluster.typed.Cluster;
import akka.event.LoggingAdapter;
import akka.io.Tcp;
import akka.io.TcpMessage;
import akka.util.ByteString;
import com.kpadmost.boardactors.WorkerAgent;
import org.json.JSONException;
import org.json.JSONObject;


import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.EntityRef;
import scala.collection.immutable.List;

import java.time.Duration;

public class ClientConnectionAgent extends AbstractActor {
    // messages
    public interface Command {}

    public static class LatencyChanged {
        final int latency;

        public LatencyChanged(int latency) {
            this.latency = latency;
        }
    }

    public static class InitEmission {
        public final int latency;

        public InitEmission(int latency) {
            this.latency = latency;
        }
    }

    static class Reconnect {
        final String whereTo;

        public Reconnect(String whereTo) {
            this.whereTo = whereTo;
        }
    }


    // fields
    private int initialLatency;

    private String clientId; // might change if agent was
    private akka.actor.ActorRef socketSender = null;
    private EntityRef<WorkerAgent.Command> worker;

    private ClusterSharding sharding;
    private LoggingAdapter log;
    // stream of messages
    private Cancellable updateEmission = null;



    public static Props create(String clientId, int latency) {
        return Props.create(ClientConnectionAgent.class, clientId, latency);
    }

    public ClientConnectionAgent(String clientId, int latency) { // TODO: dirty, get
        this.clientId = clientId;
        this.initialLatency = latency;
    }


    @Override
    public void preStart() throws Exception, Exception {
        super.preStart();
        sharding = ClusterSharding.get(Adapter.toTyped(getContext().getSystem()));
        log = getContext().getSystem().log();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(
                        Tcp.Received.class, // parsing raw data from socket
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
                            if(updateEmission != null) {
                                updateEmission.cancel();
                            }
                            getContext().stop(getSelf());
                        })
                .match(
                        WorkerAgent.BoardUpdatedResponse.class, // on tell agent, send to
                        msg -> {
                            String upd = msg.boardState;
                            socketSender.tell(TcpMessage.write(ByteString.fromString(upd + "\n")), getSelf());

                })
                .matchEquals("stop", msg -> {
                    log.info("Stopping an actor");
                    getContext().stop(getSelf());
                })
                .match(LatencyChanged.class, msg -> {
                    getContext().getSystem().log().info("changing latency on " + clientId);

                    if(updateEmission != null) {
                        updateEmission.cancel(); // BUGFIX - race condition?
                    }
                    updateEmission = getContext().getSystem().scheduler().scheduleAtFixedRate(
                            Duration.ofSeconds(1),
                            Duration.ofMillis(msg.latency), () -> {
                                ActorRef<WorkerAgent.BoardUpdatedResponse> ref = Adapter.toTyped(getSelf());
                                worker.tell(new WorkerAgent.UpdateBoard(50));
                                worker.tell(new WorkerAgent.ReadState(ref));
                            },
                            getContext().getDispatcher());


                })
                .match(InitEmission.class, msg -> {
                    getContext().getSystem().log().info("init emission on " + clientId);

                    if(worker == null) {
                        worker = sharding.entityRefFor(WorkerAgent.ENTITY_TYPE_KEY, clientId);

                        updateEmission = getContext().getSystem().scheduler().scheduleAtFixedRate(
                                Duration.ofSeconds(1),
                                Duration.ofMillis(msg.latency), () -> {
                                    ActorRef<WorkerAgent.BoardUpdatedResponse> ref = Adapter.toTyped(getSelf());
                                    worker.tell(new WorkerAgent.UpdateBoard(50));
                                    worker.tell(new WorkerAgent.ReadState(ref));
                                },
                                getContext().getDispatcher());
                    }
                })
                .match(Reconnect.class, mst -> {
                    log.info("Reconnecting " + clientId + "! to " + mst.whereTo);
                    log.info("Stop actor of " + clientId + "!");
                    final Cluster cluster = Cluster.get(Adapter.toTyped(getContext().getSystem()));
                    cluster.state().members().toStream().foreach(m -> {
                        Address s = m.address();
                        log.info("addr " + s.system() +":" + s.host() + ":" + s.getHost().orElse("sadz"));
                        return m;
                    });
                    List<Address> addr =  cluster.state().members().toStream().filter(m -> m.address().getHost().orElse("sadz").equals(mst.whereTo)).map(m -> m.address()).toList();
                    if(addr != null && !addr.isEmpty()) {
                        log.info("new addr: " + addr.toString());
                        ExternalShardAllocationClient client =
                                ExternalShardAllocation.get(Adapter.toTyped(getContext().getSystem())).getClient(WorkerAgent.ENTITY_TYPE_KEY.name());
                        client.setShardLocation("shard_" + clientId, addr.head()); // TODO: add professional mapping
                        getContext().stop(getSelf());
                    }

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
                int newLatency = js.getInt("latency");
                onInit(newLatency);
            } else if(command.equals("reconnect")) {
                String whereTo = js.getString("where");
                onReconnect(whereTo);
            } else if(command.equals("renew_connection")) {
                String oldId = js.getString("clid");
                int latency = js.getInt("latency");
                onRenew(oldId, latency);
            }
        } catch (JSONException e) {
            getContext().getSystem().log().error("FAiled parse message! " + e.getMessage());
        }
    }

    private void inspectState() {

    }

    private void onReconnect(String where) {
        getSelf().tell(new Reconnect(where), getSelf());
    }
    private void onRenew(String oldClient, int latency) {
        getSelf().tell(new TCPServiceAgent.RenewConnection(oldClient, clientId), getSelf());
        getContext().getSystem().log().info("init old on " + clientId);

        if(worker == null) {
            worker = sharding.entityRefFor(WorkerAgent.ENTITY_TYPE_KEY, clientId);
            ActorRef<WorkerAgent.BoardUpdatedResponse> ref1 = Adapter.toTyped(getSelf());
            updateEmission = getContext().getSystem().scheduler().scheduleAtFixedRate(
                    Duration.ofSeconds(1),
                    Duration.ofMillis(latency), () -> {
                        ActorRef<WorkerAgent.BoardUpdatedResponse> ref = Adapter.toTyped(getSelf());
                        worker.tell(new WorkerAgent.UpdateBoard(50));
                        worker.tell(new WorkerAgent.ReadState(ref));
                    },
                    getContext().getDispatcher());
        }
    }

    private void onChangeLatency(int newLatency) {
          getContext().parent().tell(new TCPServiceAgent.ChangeLatency(newLatency, clientId), getSelf());
    }

    private void onInit(int latency) {
        getSelf().tell(new InitEmission(latency), getSelf());
    }

}
