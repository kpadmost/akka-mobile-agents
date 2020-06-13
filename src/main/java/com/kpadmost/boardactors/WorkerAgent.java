package com.kpadmost.boardactors;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.kpadmost.board.BoardS;
import com.kpadmost.board.IBoard;
import scala.concurrent.Future;

public class WorkerAgent extends AbstractBehavior<WorkerAgent.Command> {
    public interface Command {}

    public static Behavior<Command> create() {
        return Behaviors.setup(WorkerAgent::new);
    }


    public static class UpdateLatency implements Command {
        public final int newLatency;

        public UpdateLatency(int newLatency) {
            this.newLatency = newLatency;
        }
    }

    private int latency = 30;

    private WorkerAgent(ActorContext<Command> context) {
        super(context);
        board = new BoardS();
    }

    public static class UpdateBoard implements Command {
        public final int requestId;
        public final ActorRef<BoardUpdated> replyTo;

        public UpdateBoard(int requestId, ActorRef<BoardUpdated> replyTo) {
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

        message.replyTo.tell(new BoardUpdated(message.requestId, board.toString()));
        return this;
    }

}

