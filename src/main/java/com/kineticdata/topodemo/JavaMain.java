package com.kineticdata.topodemo;

import static akka.pattern.PatternsCS.ask;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.util.Timeout;
import scala.collection.JavaConverters;
import scala.concurrent.duration.Duration;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class JavaMain {
    public static void main(String[] args) {
        ActorSystem system = ActorSystem.create("topo-demo");
        Timeout t = new Timeout(Duration.create(5, TimeUnit.SECONDS));
        ActorRef topoManager = system.actorOf(TopoManager.props());
        ActorRef topoHealthReporter = system.actorOf(TopoHealthReporter.props());

        system.scheduler().schedule(Duration.create(5, TimeUnit.SECONDS), Duration.create(30, TimeUnit.SECONDS), () -> {
            CompletableFuture<Object> response = ask(topoManager, GetTopoReport$.MODULE$, t).toCompletableFuture();
            try {
                Object result = response.join();
                if (result instanceof TopoNotReady$) {
                    System.out.println("Topology is not ready for a report, probably still joining the cluster.");
                } else if (result instanceof TopoReport) {
                    List<TopoMetric> metrics = JavaConverters.bufferAsJavaList(((TopoReport) result).metrics());
                    metrics.forEach(nodeMetric -> {
                        System.out.println("metric from: " + nodeMetric.address() + " " + nodeMetric.name() + " = " + nodeMetric.value());
                    });
                }
            } catch(Exception e) {
                System.out.println("Failed to get report: " + e.getMessage());
            }
        }, system.dispatcher());
    }

}
