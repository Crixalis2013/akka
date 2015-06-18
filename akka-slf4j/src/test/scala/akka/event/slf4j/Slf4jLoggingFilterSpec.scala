/**
 * Copyright (C) 2009-2015 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.event.slf4j

import language.postfixOps
import akka.testkit.AkkaSpec
import akka.actor.{ DiagnosticActorLogging, Actor, ActorLogging, Props }
import scala.concurrent.duration._
import akka.event.Logging
import ch.qos.logback.core.OutputStreamAppender
import java.io.StringWriter
import java.io.ByteArrayOutputStream
import org.scalatest.BeforeAndAfterEach
import akka.actor.ActorRef
import akka.event.Logging.InitializeLogger
import akka.event.Logging.LoggerInitialized
import akka.event.Logging.LogEvent
import akka.testkit.TestProbe
import akka.event.Logging.Warning
import akka.event.Logging.Info
import akka.event.Logging.Debug
import akka.event.LoggingAdapter
import akka.event.LogSource

object Slf4jLoggingFilterSpec {

  // This test depends on logback configuration in src/test/resources/logback-test.xml

  val config = """
    akka {
      loglevel = DEBUG
      loggers = ["akka.event.slf4j.Slf4jLoggingFilterSpec$TestLogger"]
      logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
    }
    """

  final case class SetTarget(ref: ActorRef)

  class TestLogger extends Actor {
    var target: Option[ActorRef] = None
    override def receive: Receive = {
      case InitializeLogger(bus) ⇒
        bus.subscribe(context.self, classOf[SetTarget])
        sender() ! LoggerInitialized
      case SetTarget(ref) ⇒
        target = Some(ref)
        ref ! ("OK")
      case event: LogEvent ⇒
        println("# event: " + event)
        target foreach { _ ! event }
    }
  }

  class DebugLevelProducer extends Actor with ActorLogging {
    def receive = {
      case s: String ⇒
        log.warning(s)
        log.info(s)
        println("# DebugLevelProducer: " + log.isDebugEnabled)
        log.debug(s)
    }
  }

  class WarningLevelProducer extends Actor with ActorLogging {
    def receive = {
      case s: String ⇒
        log.warning(s)
        log.info(s)
        log.debug(s)
    }
  }

}

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class Slf4jLoggingFilterSpec extends AkkaSpec(Slf4jLoggingFilterSpec.config) with BeforeAndAfterEach {
  import Slf4jLoggingFilterSpec._

  "Slf4jLoggingFilter" must {

    "use configured LoggingFilter at debug log level in logback conf" in {
      import LogSource.fromClass
      val log1 = Logging(system, classOf[DebugLevelProducer])
      log1.isDebugEnabled should be(true)
      log1.isInfoEnabled should be(true)
      log1.isWarningEnabled should be(true)
      log1.isErrorEnabled should be(true)
    }

    "use configured LoggingFilter at warning log level in logback conf" in {
      import LogSource.fromClass
      val log1 = Logging(system, classOf[WarningLevelProducer])
      log1.isDebugEnabled should be(false)
      log1.isInfoEnabled should be(false)
      log1.isWarningEnabled should be(true)
      log1.isErrorEnabled should be(true)
    }

    "filter ActorLogging at debug log level with logback conf" in {
      val probe = TestProbe()
      system.eventStream.publish(SetTarget(probe.ref))
      probe.expectMsg("OK")
      val debugLevelProducer = system.actorOf(Props[DebugLevelProducer], name = "debugLevelProducer")
      debugLevelProducer ! "test1"
      probe.expectMsgType[Warning].message should be("test1")
      probe.expectMsgType[Info].message should be("test1")
      probe.expectMsgType[Debug].message should be("test1")
    }

    "filter ActorLogging at warning log level with logback conf" in {
      val probe = TestProbe()
      system.eventStream.publish(SetTarget(probe.ref))
      probe.expectMsg("OK")
      val debugLevelProducer = system.actorOf(Props[WarningLevelProducer], name = "warningLevelProducer")
      debugLevelProducer ! "test2"
      probe.expectMsgType[Warning].message should be("test2")
      probe.expectNoMsg(500.millis)
    }
  }

}