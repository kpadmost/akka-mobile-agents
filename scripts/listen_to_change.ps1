Function Receive-TCPMessage {
    Param ( 
        [Parameter(Mandatory=$true, Position=0)]
        [ValidateNotNullOrEmpty()] 
        [int] $Port
    ) 
    Process {
        # change latency of containers via docker command. Not working properly(no visible effect), feel free to fix it :)
        Try { 
			$pseed = 10051
			$pc1 = 10056
			$pc2 = 10057
			echo "Here!"
			docker exec -u root akka-sample-cluster-docker-compose-java_seed_1 tc qdisc add dev eth0 root handle 1: prio priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
			docker exec -u root akka-sample-cluster-docker-compose-java_seed_1 tc qdisc add dev eth0 parent 1:2 handle 20: netem delay 5ms
			docker exec -u root akka-sample-cluster-docker-compose-java_seed_1 tc filter add dev eth0 parent 1:0 protocol ip u32 match ip sport $pseed  0xffff flowid 1:2
			
			
			docker exec -u root akka-sample-cluster-docker-compose-java_c1_1 tc qdisc add dev eth0 root handle 1: prio priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
			docker exec -u root akka-sample-cluster-docker-compose-java_c1_1 tc qdisc add dev eth0 parent 1:2 handle 20: netem delay 5ms
			docker exec -u root akka-sample-cluster-docker-compose-java_c1_1 tc filter add dev eth0 parent 1:0 protocol ip u32 match ip sport $pc1 0xffff flowid 1:2
			
			
			docker exec -u root akka-sample-cluster-docker-compose-java_c2_1 tc qdisc add dev eth0 root handle 1: prio priomap 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
			docker exec -u root akka-sample-cluster-docker-compose-java_c2_1 tc qdisc add dev eth0 parent 1:2 handle 20: netem delay 5ms
			
			docker exec -u root akka-sample-cluster-docker-compose-java_c2_1 tc filter add dev eth0 parent 1:0 protocol ip u32 match ip sport $pc2 0xffff flowid 1:2
		
		
            # Set up endpoint and start listening
            $endpoint = new-object System.Net.IPEndPoint([ipaddress]::any,$port) 
            $listener = new-object System.Net.Sockets.TcpListener $EndPoint
            $listener.start() 
 
            # Wait for an incoming connection 
            $data = $listener.AcceptTcpClient() 
			echo "Accepted!"
            # Stream setup
            $stream = $data.GetStream() 
            $bytes = New-Object System.Byte[] 1024

            # Read data from stream and write it to host
            while (($i = $stream.Read($bytes,0,$bytes.Length)) -ne 0){
                $EncodedText = New-Object System.Text.ASCIIEncoding
                $data = $EncodedText.GetString($bytes,0, $i)
				$js = ConvertFrom-Json($data)
				
				echo $js
				$node = $js.node
				
				$latency = $js.latency
				$latencyf = "$latency" + "ms"
				$nodep = Get-Variable -Name "p$node" -ValueOnly
				
                docker exec -u root akka-sample-cluster-docker-compose-java_"$node"_1  tc qdisc replace dev eth0 parent 1:2 handle 20: netem delay $latencyf
				
            }
         
            # Close TCP connection and stop listening
            $stream.close()
            $listener.stop()
        }
        Catch {
            "Receive Message failed with: `n" + $Error[0]
			#docker exec -u root akka-sample-cluster-docker-compose-java_seed_1  tc qdisc add dev eth0 root netem delay 10ms OLD
		
		}
		
		
		
    }
}