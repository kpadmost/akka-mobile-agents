package com.kpadmost;

import akka.NotUsed;
import akka.actor.Cancellable;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.stream.javadsl.*;
import akka.util.ByteString;
import com.kpadmost.connection.ConnectionAgent;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.sql.Connection;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class ClusteringApp {

  public static void main(String[] args) {
    Config config = ConfigFactory.load();
    String clusterName = config.getString("clustering.cluster.name");

    ActorSystem<?> system = ActorSystem.create(ClusterListener.create(), clusterName);
//    int port = config.getInt("networking.port");
//
//    final Source<Tcp.IncomingConnection, CompletionStage<Tcp.ServerBinding>> connections =
//            Tcp.get(system).bind("0.0.0.0", port);
////    final ActorRef<ConnectionAgent.Command> connagent = system.tell();
//    connections.to(Sink.foreach(
//            connection -> {
//
//              system.log().info("Newconn1! " + connection.remoteAddress() + ", " + connection.localAddress() );
//                final String mess = "Welcome to " + clusterName + ", " + connection.remoteAddress() + ", " + connection.localAddress();
//             final Flow servlog = echoLogic(system, mess);
//
////              connection.handleWith(servlog, system);
//            }
//    )).run(system);

  }


  private static Flow<ByteString, ByteString, NotUsed> echoLogic(final ActorSystem system, String mess) {

      Source<ByteString, NotUsed> so = Source.single(mess).map(ByteString::fromString);

      return Flow.of(ByteString.class)
              .via(Framing.delimiter(ByteString.fromString("bye"), 200))
              .map(ByteString::utf8String)
              .map(s -> {
                  system.log().info("message: " + s);
                  return s + "\n";
              })

              .map(ByteString::fromString)
              .merge(so)
              ;
  }

}
