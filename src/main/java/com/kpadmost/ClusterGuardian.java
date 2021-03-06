package com.kpadmost;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import akka.cluster.ClusterEvent;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Subscribe;
import akka.io.Tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.fasterxml.jackson.module.paranamer.ParanamerModule;
import com.fasterxml.jackson.module.paranamer.ParanamerOnJacksonAnnotationIntrospector;
import com.kpadmost.boardactors.WorkerAgent;
import com.kpadmost.connection.TCPServiceAgent;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;


public final class ClusterGuardian extends AbstractBehavior<ClusterEvent.ClusterDomainEvent> {

  public static Behavior<ClusterEvent.ClusterDomainEvent> create() {
    return Behaviors.setup(ClusterGuardian::new);
  }

  private ClusterGuardian(ActorContext<ClusterEvent.ClusterDomainEvent> context) {
    super(context);
    context.getLog().debug("starting up cluster listener...");
    final Cluster cluster = Cluster.get(context.getSystem());
    cluster.subscriptions().tell(Subscribe.create(context.getSelf(), ClusterEvent.ClusterDomainEvent.class));
    listenOnConnections();
  }

  private void listenOnConnections() {
      Config config = ConfigFactory.load();
      ObjectMapper mapper = new ObjectMapper();
        // either via module
      mapper.registerModule(new ParanamerModule());
        // or by directly assigning annotation introspector (but not both!)
      mapper.setAnnotationIntrospector(new ParanamerOnJacksonAnnotationIntrospector());
      String clusterName = config.getString("clustering.cluster.name");

      ActorSystem<?> system = getContext().getSystem();
      int port = config.getInt("networking.port");

      final akka.actor.ActorRef tcpManager = Tcp.get(getContext().getSystem()).manager();

      final akka.actor.ActorRef connagent = Adapter.actorOf(getContext(), TCPServiceAgent.props(tcpManager, port));
      WorkerAgent.initSharding(system, 100);
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
        }).onMessage(ClusterEvent.MemberEvent.class, event -> {
          getContext().getLog().info("Member Event: " + event.toString());
          return this;
        }).build();
  }
}
