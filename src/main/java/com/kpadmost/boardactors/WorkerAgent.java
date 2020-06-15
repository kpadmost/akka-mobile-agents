package com.kpadmost.boardactors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.actor.ActorRef;
import com.kpadmost.board.BoardS;
import com.kpadmost.board.IBoard;
import scala.concurrent.Future;

public class WorkerAgent extends AbstractBehavior<WorkerAgent.Command> {
    public interface Command {}

    public static Behavior<Command> create() {
        return Behaviors.setup();
    }



    private final String entityId;


    private WorkerAgent(ActorContext<Command> context, String entityId) {
        super(context);
        board = new BoardS();
        this.entityId = entityId;
    }

    public static class UpdateBoard implements Command {
        public final int requestId;
        public final akka.actor.ActorRef replyTo;

        public UpdateBoard(int requestId, ActorRef replyTo) {
            this.requestId = requestId;
            this.replyTo = replyTo;
        }
    }

    public static class BoardUpdated {
        public final int requestId;
        public final String boardState;

        public BoardUpdated(int requestId, String boardState) {
            this.requestId = requestId;
            this.boardState = boardState;
        }
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
        getContext().getLog().info("Updating board " + getContext().getSelf().path().name());
        message.replyTo.tell(new BoardUpdated(message.requestId, board.toString()), Adapter.toClassic(getContext().getSelf()));
        return this;
    }

}

