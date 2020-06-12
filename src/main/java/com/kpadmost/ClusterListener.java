package com.kpadmost;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.ClusterEvent;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Subscribe;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.stream.javadsl.Tcp;
import com.kpadmost.connection.ConnectionAgent;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.concurrent.CompletionStage;

public final class ClusterListener extends AbstractBehavior<ClusterEvent.ClusterDomainEvent> {

  public static Behavior<ClusterEvent.ClusterDomainEvent> create() {
    return Behaviors.setup(ClusterListener::new);
  }

  private ClusterListener(ActorContext<ClusterEvent.ClusterDomainEvent> context) {
    super(context);
    context.getLog().debug("starting up cluster listener...");
    final Cluster cluster = Cluster.get(context.getSystem());
    cluster.subscriptions().tell(Subscribe.create(context.getSelf(), ClusterEvent.ClusterDomainEvent.class));
    listenOnConnections();
  }

  private void listenOnConnections() {
      Config config = ConfigFactory.load();
      String clusterName = config.getString("clustering.cluster.name");

      ActorSystem<?> system = getContext().getSystem();
      int port = config.getInt("networking.port");

      final Source<Tcp.IncomingConnection, CompletionStage<Tcp.ServerBinding>> connections =
              Tcp.get(system).bind("0.0.0.0", port);
    final ActorRef<ConnectionAgent.Command> connagent = getContext().spawn(ConnectionAgent.create(), "mainconn-agent");
      connections.to(Sink.foreach(
              connection -> {

                  system.log().info("Newconn1! " + connection.remoteAddress() + ", " + connection.localAddress() );
                  final String mess = "Welcome to " + clusterName + ", " + connection.remoteAddress() + ", " + connection.localAddress();
//                  final Flow servlog = echoLogic(system, mess);
                connagent.tell(new ConnectionAgent.InitConnectionRequest(5, connection));
//              connection.handleWith(servlog, system);
              }
      )).run(system);
  }

  @Override
  public Receive<ClusterEvent.ClusterDomainEvent> createReceive() {
    return newReceiveBuilder()
        .onMessage(ClusterEvent.MemberUp.class, event -> {
          getContext().getLog().info("Member is Up: {}", event.member().address());
          return this;
        }).onMessage(ClusterEvent.UnreachableMember.class, event -> {
          getContext().getLog().info("Member detected as unreachable: {}", event.member().address());
          return this;
        }).onMessage(ClusterEvent.MemberRemoved.class, event -> {
          getContext().getLog().info("Member is Removed: {} after {}", event.member().address(), event.previousStatus());
          return this;
        }).onMessage(ClusterEvent.MemberRemoved.class, event -> {
          getContext().getLog().info("Member Event: " + event.toString());
          return this;
        }).build();
  }
}
