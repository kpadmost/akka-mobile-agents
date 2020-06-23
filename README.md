##### Akka Mobile Agents in Mobile Cloud Project.

Fork of https://github.com/akka/akka-sample-cluster-docker-compose-java

Launching:
1. Prerequisites:
    * Docker Compose
    * Cassandra server on port 9432(lookup in application.conf. Also you may change logging level in there and logback)
    * Java 1.8(Akka java image does not support higher version)
    * open ports as in docker-compose.yml. Note that 1600-1602 are for inner cluster communication, but 100** are exposed ports
2. Notes:
    * feel free to remove priveleged and add-cap from docker-compose(added for latency testing)
    * communication with outer devices is via TCP socket, based on JSON messaging. Protocol is described in docs. Feel 
    free to ask if you need it.
    * listen_to_change is powershell script for changing latency in containers
 
 
 
### How to Run
In SBT, just run docker:publishLocal to create a local docker container.

To run the cluster, run docker-compose up. This will create 3 nodes, a seed and two regular members, called seed, c1, and c2 respectively.

While running, try opening a new terminal and (from the same directory) try things like docker-compose down seed and watch the cluster nodes respond.
