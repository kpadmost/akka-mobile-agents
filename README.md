##### Akka Mobile Agents in Mobile Cloud Project.

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
 
