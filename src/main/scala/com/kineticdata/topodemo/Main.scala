package com.kineticdata.topodemo

import akka.actor.ActorSystem
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main extends  App {
  implicit val system = ActorSystem("topo-demo")
  implicit val materializer = ActorMaterializer()
  implicit val timeout = Timeout(5 seconds)
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val scheduler = system.scheduler

  val topoManager = system.actorOf(TopoManager.props)

  val topoHealthReporter = system.actorOf(TopoHealthReporter.props)

  system.scheduler.schedule(5 seconds, 30 seconds)(
    (topoManager ? GetTopoReport) onComplete {
      case Success(response) => response match {
        case TopoNotReady => println("Topology is not ready for a report, probably still joining the cluster.")
        case report: TopoReport => report.metrics foreach {
          nodeReport: TopoMetric => println(s"metric from: ${nodeReport.address}: ${nodeReport.name} = ${nodeReport.value}")
        }
      }
      case Failure(t) => println(s"failed to get report: ${t.getMessage()}")
    }
  )
}
