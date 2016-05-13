# webconnect 1
# webconnect localhost:8080 1 test1 1 1
# audioconnect
# audioconnect blank
# audioconnect ../samples/test1.raw
java -cp target/scala-2.11/together-prototype-assembly-0.1.0-SNAPSHOT.jar together.audio.AudioClient
