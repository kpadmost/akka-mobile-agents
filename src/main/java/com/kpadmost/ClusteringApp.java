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

    }

}
