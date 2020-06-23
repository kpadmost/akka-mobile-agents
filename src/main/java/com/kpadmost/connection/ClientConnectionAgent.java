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



    private static class InitEmission { // connecting for the first time

    }

    static class Reconnect { // client wishes to reconnect somewhere else
        final String whereTo;

        Reconnect(String whereTo) {
            this.whereTo = whereTo;
        }
    }


    // fields


    private String clientId; // might change if agent was
    private akka.actor.ActorRef clientSocket = null; // referes to client
    private EntityRef<WorkerAgent.Command> worker; // holds board

    private ClusterSharding sharding;
    private LoggingAdapter log;
    // stream of messages
    private Cancellable updateEmission = null;



    static Props create(String clientId) {
        return Props.create(ClientConnectionAgent.class, clientId);
    }

    public ClientConnectionAgent(String clientId) { // TODO: dirty, get
        this.clientId = clientId;
    }


    @Override
    public void preStart() throws Exception {
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
                            if(clientSocket == null)
                                clientSocket = getSender();

                            final ByteString data = msg.data();
                            System.out.println(data.utf8String());
                            parseConnectionData(data.utf8String());
                        })
                .match(
                        Tcp.ConnectionClosed.class,
                        msg -> {
                            getContext().getSystem().log().info("Stop actor of " + clientId + "!");
                            cancelEmission();
                            getContext().stop(getSelf());
                        })
                .match(
                        WorkerAgent.BoardUpdatedResponse.class, // on tell agent, send to
                        msg -> {
                            String upd = msg.boardState;
                            try {
                                int is = Integer.parseInt(msg.boardState.split(":")[2]);
                                if(is % 50 == 0)
                                log.debug("c send " + is);
                            } catch (Exception e) {

                            }
                            clientSocket.tell(TcpMessage.write(ByteString.fromString(upd + "\n")), getSelf());

                })
                .match(InitEmission.class, msg -> {
                    getContext().getSystem().log().info("init emission on " + clientId);
                    instatiateEmission();

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
                    List<Address> addr =  cluster
                            .state().members().toStream().
                                    filter(m -> m.address().getHost().orElse("not_an_adress").equals(mst.whereTo))
                            .map(m -> m.address()).toList();
                    if(addr != null && !addr.isEmpty()) {
                        log.info("new addr: " + addr.toString());
                        cancelEmission();
                        ExternalShardAllocationClient client =
                                ExternalShardAllocation.get(Adapter.toTyped(getContext().getSystem())).getClient(WorkerAgent.ENTITY_TYPE_KEY.name());
                        client.setShardLocation("shard_" + clientId, addr.head()); // TODO: add professional mapping
                        getContext().stop(getSelf());
                    }

                })
                .build();
    }

    // function for parsing incoming JSON messages. For better experience use Akka Streams
    private void parseConnectionData(String msg) {
        try {
            JSONObject js = new JSONObject(msg);
            String command = js.getString("command"); // TODO add enum
            switch (command) {
                case "init":
                    onInit();
                    break;
                case "reconnect":
                    String whereTo = js.getString("where");
                    onReconnect(whereTo);
                    break;
                case "renew_connection":
                    String oldId = js.getString("clid");
                    onRenew(oldId);
                    break;
            }
        } catch (JSONException e) {
            getContext().getSystem().log().error("FAiled parse message! " + e.getMessage());
        }
    }


    private void onReconnect(String where) {
        getSelf().tell(new Reconnect(where), getSelf());
    }

    private void onRenew(String oldClient) {
        getSelf().tell(new TCPServiceAgent.RenewConnection(oldClient, clientId), getSelf());
        clientId = oldClient;
        getContext().getSystem().log().info("init old on " + clientId);

        instatiateEmission();

    }

    // function for asking repeatedly workeractor to both update board and return updated board state.
    // Why don't do it at the same time? UpdateBoard changes state, and according to the EventSource logic, while restoring
    // state, responding would be repeated. ReadBoard does not change state so no persistence. Therefore, two messages
    private void instatiateEmission() {
        if(worker == null) {
            worker = sharding.entityRefFor(WorkerAgent.ENTITY_TYPE_KEY, clientId);
        }
        cancelEmission();
            updateEmission = getContext().getSystem().scheduler().scheduleAtFixedRate(
                    Duration.ofSeconds(1),
                    Duration.ofMillis(40), () -> {
                        ActorRef<WorkerAgent.BoardUpdatedResponse> ref = Adapter.toTyped(getSelf());
                        worker.tell(new WorkerAgent.UpdateBoard(1));
                        worker.tell(new WorkerAgent.ReadBoardState(ref));
                    },
                    getContext().getDispatcher());

    }

    private void cancelEmission() {
        if(updateEmission != null) {
            updateEmission.cancel();
        }
    }

    private void onInit() {
        getSelf().tell(new InitEmission(), getSelf());
    }

}
