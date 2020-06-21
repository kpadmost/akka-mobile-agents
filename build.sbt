organization := "com.mlh"

/* scala versions and options */
//scalaVersion := "2.12"
scalaVersion := "2.13.1"
resolvers += Resolver.bintrayRepo("dnvriend", "maven")

// These options will be used for *all* versions.
scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-encoding", "UTF-8",
  "-Xlint",
  "-target", "jvm-1.8"
)
javacOptions ++= Seq("-source", "1.8", "-parameters")
val akka = "2.6.6"
//val akka = "2.5.15"
/* dependencies */
libraryDependencies ++= Seq (
  // -- Logging --
  "ch.qos.logback"    % "logback-classic"           % "1.2.3",
  // -- Akka --
  "com.typesafe.akka" %% "akka-actor-typed"         % akka,
  "com.typesafe.akka" %% "akka-cluster-typed"       % akka,
  "com.typesafe.akka" %% "akka-persistence-typed" % akka,
  "com.typesafe.akka" %% "akka-serialization-jackson" %  akka,
  "com.fasterxml.jackson.module" % "jackson-module-paranamer" % "2.2.3",

"com.typesafe.akka" %% "akka-cluster-sharding-typed" % akka,
  "com.typesafe.akka" %% "akka-persistence-cassandra" % "1.0.1",
  "org.json" % "json"   % "20090211",
  "com.typesafe.akka" %% "akka-persistence-query" % akka,
  "com.typesafe.akka" %% "akka-cluster-tools" % akka,
//  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.2"
//  "com.lightbend.akka" %% "akka-persistence-couchbase" % "1.0"

)

maintainer := "Michael Hamrah <m@hamrah.com>"

version in Docker := "latest"

dockerExposedPorts in Docker := Seq(1600)

dockerEntrypoint in Docker := Seq("sh", "-c", "bin/clustering $*")

dockerRepository := Some("lightbend")

dockerBaseImage := "java"
enablePlugins(JavaAppPackaging)
