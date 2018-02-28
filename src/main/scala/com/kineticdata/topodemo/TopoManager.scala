package com.kineticdata.topodemo

import akka.actor.{Actor, ActorLogging, Address, Props, Stash}
import akka.cluster.{Cluster, MemberStatus}
import akka.cluster.ClusterEvent._
import akka.cluster.metrics.{ClusterMetricsChanged, ClusterMetricsExtension}
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe, Unsubscribe}

import scala.collection.mutable.ArrayBuffer

sealed trait TopoManagerMessages
final case object GetTopoReport extends TopoManagerMessages
final case object PerformTopoReport extends TopoManagerMessages
final case class RcvdTopoReport(address: Address, name: String, value: String) extends TopoManagerMessages
final case class TopoReport(metrics: ArrayBuffer[TopoMetric]) extends TopoManagerMessages
final case object TopoNotReady extends TopoManagerMessages

object TopoManager {
  def props: Props = Props[TopoManager]
}

class TopoManager extends Actor with ActorLogging with Stash {
  val cluster = Cluster(context.system)
  val clusterMetrics = ClusterMetricsExtension(context.system)
  val mediator = DistributedPubSub(context.system).mediator
  var nodes = Set.empty[Address]

  // Register to hear cluster member events and topo reports.
  override def preStart(): Unit = {
    log.info("Starting up Topo Manager.")
    // Subscribe to cluster events, e.g. MemberUp, MemberDown
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents, classOf[MemberEvent])
    // Subscribe to cluster metrics events, e.g. ClusterMetricsChanged.
    clusterMetrics.subscribe(self)
    // Subscribe to "topo-report" in order to hear replies to topology requests.
    mediator ! Subscribe("topo-report", self)
  }
  // When this actor is stopped, unregister ourselves.
  override def postStop(): Unit = {
    log.info("Shutting down Topo Manager")
    cluster unsubscribe self
    clusterMetrics unsubscribe self
    mediator ! Unsubscribe("topo-report", self)
  }

  // When we 'become' a topology collector we will need to maintain a reference to who started
  // the request for topology so we can reply back to them.
  var topoRequestor = Actor.noSender
  // This is a mutable store, for now, that holds the topology replies from nodes.
  var topoMetrics = new ArrayBuffer[TopoMetric]()

  override def receive: Receive = {
    // If we get an updated state of the cluster, grab the interesting nodes.
    case state: CurrentClusterState =>
      nodes = state.members.collect {
        case m if m.status == MemberStatus.Up => m.address
      }
    // A memeber was added. Keep track of it.
    case MemberUp(member) =>
      nodes += member.address
    // A member was removed or dropped, remove it from our list.
    case MemberRemoved(member, _) =>
      nodes -= member.address
    case _: MemberEvent => // Ignore other member events for now.
    // Experimenting with the cluster metrics extension.
    case ClusterMetricsChanged(clusterMetrics) => // Ignore these for now.
      // clusterMetrics.foreach {
      //  nodeMetrics =>
      //    println(nodeMetrics)
      //}
    case GetTopoReport =>
      if (nodes.size > 0) {
        // Save a ref to who requested the report.
        topoRequestor = sender
        // Reset the list of metrics
        topoMetrics.clear()
        // Change state to do topo collection.
        context.become(topoCollector)
        mediator ! Publish("topo-report", PerformTopoReport)
      } else {
        sender ! TopoNotReady
      }
  }

  def topoCollector: Receive = {
    case RcvdTopoReport(address, name, value) =>
      topoMetrics += new TopoMetric(address, name, value)
      if(topoMetrics.length == nodes.size) {
        unstashAll()
        context.unbecome()
        topoRequestor ! TopoReport(topoMetrics)
      }
    case msg => stash()
  }
}
