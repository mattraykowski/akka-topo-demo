package com.kineticdata.topodemo

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe

object TopoHealthReporter {
  def props: Props = Props[TopoHealthReporter]
}

class TopoHealthReporter extends Actor with ActorLogging {
  val cluster = Cluster(context.system)
  val mediator = DistributedPubSub(context.system).mediator

  override def preStart() = {
    log.info("Starting up Topo Health Reporter")
    mediator ! Subscribe("topo-report", self)
  }

  override def receive: Receive = {
    case PerformTopoReport =>
      sender ! RcvdTopoReport(cluster.selfAddress, "xyz", "50")
  }
}
