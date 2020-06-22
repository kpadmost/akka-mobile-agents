package com.kpadmost.boardactors;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.actor.typed.ActorRef;
import akka.cluster.sharding.external.ExternalShardAllocationStrategy;
import akka.cluster.sharding.typed.javadsl.ClusterSharding;
import akka.cluster.sharding.typed.javadsl.Entity;
import akka.cluster.sharding.typed.javadsl.EntityTypeKey;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.Effect;
import akka.persistence.typed.javadsl.EventHandler;
import akka.util.Timeout;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kpadmost.board.BoardS;
//import akka.serialization

import akka.persistence.typed.javadsl.EventSourcedBehavior;
import akka.persistence.typed.PersistenceId;
import com.kpadmost.serialization.CborSerializable;

import java.util.concurrent.TimeUnit;



public class WorkerAgent extends EventSourcedBehavior<WorkerAgent.Command, WorkerAgent.Event, WorkerAgent.State> {




    public interface Command extends CborSerializable {}

    public interface Event extends CborSerializable {}

    public interface State extends CborSerializable {
        public State updateBoard();
    }


    // message
    public static class UpdateBoard implements Command {
        public final int requestId;


        public UpdateBoard(@JsonProperty("requestId") int requestId) {
            this.requestId = requestId;
        }

    }

    public static class ReadBoardState implements Command {
        public final ActorRef<BoardUpdatedResponse> sender;



        public ReadBoardState(@JsonProperty("sender")  ActorRef<BoardUpdatedResponse> sender) {
            this.sender = sender;
        }

    }

    public static class BoardUpdatedResponse implements CborSerializable {
        public final String boardState;

        public BoardUpdatedResponse(@JsonProperty("boardState")  String boardState) {
            this.boardState = boardState;
        }
    }

    // response
    public static class BoardUpdated implements Event { // event
        final int rq;
        public BoardUpdated(@JsonProperty("rq") int rq){ this.rq = rq; }
    }


    public static class BoardState implements State {
        private BoardS board;

        private BoardState(BoardS board) {
            this.board = new BoardS(board);
        }

        public BoardState() {

            System.out.println("conns ");
            board = new BoardS();
        }


        public BoardState updateBoard() {
            this.board.update();
            return new BoardState(this.board);
        }

        @Override
        public String toString() {
            return board.toString();
        }
    }

    @Override
    public State emptyState() {
        return new BoardState();
    }




    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(UpdateBoard.class, this::onUpdateBoard)
                .onCommand(ReadBoardState.class, this::onReadSender)
                .build();
    }


    private Effect<Event, State> onReadSender(State state, ReadBoardState cmd) {
        return Effect().none()
                .thenRun(newst -> {
                    cmd.sender.tell(new BoardUpdatedResponse(newst.toString()));
                });
    }



    private Effect<Event, State> onUpdateBoard(State state, UpdateBoard cmd) {
        return Effect()
                .persist(new BoardUpdated(1));

    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder()
                .forAnyState()
                .onEvent(BoardUpdated.class, (State s, BoardUpdated bu) -> s.updateBoard())
                .build();
    }


    public static void initSharding(ActorSystem<?> system, int maxShards) {
        ClusterSharding.get(system).init(Entity.of(ENTITY_TYPE_KEY, ctx -> create(ctx.getEntityId(), PersistenceId.of(ENTITY_TYPE_KEY.name(), ctx.getEntityId()))).withAllocationStrategy(
                new ExternalShardAllocationStrategy(system, ENTITY_TYPE_KEY.name(), Timeout.apply(2, TimeUnit.SECONDS))
        ).withMessageExtractor(new MyMessageExtractor()));

    }

    public static Behavior<Command> create(String entityId, PersistenceId persistenceId) {
        return Behaviors.setup(ctx -> new WorkerAgent(entityId, persistenceId));
    }


    public static final EntityTypeKey<Command> ENTITY_TYPE_KEY =
            EntityTypeKey.create(Command.class, "BoardWorker");


    private final String entityId;


    private WorkerAgent(String entityId, PersistenceId persistenceId) {
        super(persistenceId);
        this.entityId = entityId;
    }

}

