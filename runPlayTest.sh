sbt assembly
java -Dscala.concurrent.context.numThreads=8 -Dscala.concurrent.context.maxThreads=8 -cp target/scala-2.11/together-prototype-assembly-0.1.0-SNAPSHOT.jar together.PlayTest
