package com.kpadmost.boardactors;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.actor.ActorRef;
import akka.cluster.sharding.ShardRegion;
import akka.cluster.sharding.external.ExternalShardAllocationStrategy;
import akka.cluster.sharding.typed.HashCodeMessageExtractor;
import akka.cluster.sharding.typed.ShardingEnvelope;
import akka.cluster.sharding.typed.ShardingMessageExtractor;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.dispatch.Envelope;
import akka.util.Timeout;
import com.kpadmost.board.BoardS;
import com.kpadmost.board.IBoard;
import scala.concurrent.Future;

import java.util.concurrent.TimeUnit;

public class WorkerAgent extends AbstractBehavior<WorkerAgent.Command> {
    public interface Command {}


    // message
    public static class UpdateBoard implements Command {
        public final int requestId;
        public final akka.actor.ActorRef replyTo;

        public UpdateBoard(int requestId, ActorRef replyTo) {
            this.requestId = requestId;
            this.replyTo = replyTo;
        }
    }

    // response
    public static class BoardUpdated {
        public final int requestId;
        public final String boardState;

        public BoardUpdated(int requestId, String boardState) {
            this.requestId = requestId;
            this.boardState = boardState;
        }
    }


    private static class MyMessageExtractor<M> extends  ShardingMessageExtractor<ShardingEnvelope<M> , M> {
        @Override
        public String entityId(ShardingEnvelope<M> message) {
            return message.entityId();
        }

        @Override
        public String shardId(String entityId) {
            return "shard_" + entityId;
        }

        @Override
        public M unwrapMessage(ShardingEnvelope<M> message) {
            return message.message();
        }
    }

    public static void initSharding(ActorSystem<?> system, int maxShards) {
        ClusterSharding.get(system).init(Entity.of(ENTITY_TYPE_KEY, ctx -> create(ctx.getEntityId())).withAllocationStrategy(
                new ExternalShardAllocationStrategy(system, ENTITY_TYPE_KEY.name(), Timeout.apply(2, TimeUnit.SECONDS))
        ).withMessageExtractor(new MyMessageExtractor()));


    }

    public static Behavior<Command> create(String entityId) {
        return Behaviors.setup(ctx -> new WorkerAgent(ctx, entityId));
    }


    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY =
            EntityTypeKey.create(Command.class, "BoardWorker");


    private final String entityId;


    private WorkerAgent(ActorContext<Command> context, String entityId) {
        super(context);
        board = new BoardS();
        this.entityId = entityId;
    }



    private IBoard board;

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
                .onMessage(UpdateBoard.class, this::onUpdateBoard)
                .build();
    }




    private void update() {
        board.update();
    }

    private Behavior<Command> onUpdateBoard(UpdateBoard message) {
        update();
//        Future<BoardUpdated> f = future(() -> {
//
//        });
        message.replyTo.tell(new BoardUpdated(message.requestId, board.toString()), Adapter.toClassic(getContext().getSelf()));
        return this;
    }

}

