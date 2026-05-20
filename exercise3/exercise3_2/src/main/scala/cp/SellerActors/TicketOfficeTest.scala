package cp.SellerActors

import akka.actor._
import akka.event.Logging

object TicketOfficeTest extends App {
  case class ToSell(n: Int)
  case class Buy(n: Int)
  case class Report(n: Int)
  case object Balance

  class SellerActor(threshold: Int,delegate: Int) extends Actor {
    val log = Logging(context.system,this)
    var tickets: Int = 0
    var children_total: Int = 0
    def receive: Actor.Receive = {
      case ToSell(n) =>
        tickets += n
        if (tickets > threshold) {
          children_total += 1
          log.info(s"Threshold reached with $tickets, start ChildOffice$children_total")
          tickets -= delegate
          context.actorOf(Props(new ChildSellerActor(delegate)),s"ChildOffice$children_total")
        }
      case Buy(n) =>
        if (tickets >= n) {
          tickets -= n
        }
        else {
          log.info(s"Main office could not provide $n tickets, only $tickets available")
        }
        for (child <- context.children) {
          child ! Buy(n)
        }
      case Report(n) =>
        tickets += n
        log.info(s"Child Office return $n tickets")
        if (tickets > threshold) {
          children_total += 1
          log.info(s"Threshold reached with $tickets, start ChildOffice$children_total")
          tickets -= delegate
          context.actorOf(Props(new ChildSellerActor(delegate)),s"ChildOffice$children_total")
        }
      case "Bye" =>
        log.info(s"Main office terminated with $tickets tickets!")
        for (child <- context.children) {
          child ! Balance
        }
        context.stop(self)
    }
  }

  class ChildSellerActor(t: Int) extends Actor {
    val log = Logging(context.system,this)
    var tickets: Int = t
    def alive: Actor.Receive = {
      case Buy(n) =>
        if (tickets < n) {
          log.info(s"${self.path.name} could not provide $n tickets, only $tickets remaining. Terminate ChildActor")
          context.parent ! Report(tickets)
          context.become(dead)
        }
        else {
          tickets -= n
        }
      case Balance =>
        log.info(s"${self.path.name} has $tickets tickets at the end")
    }
    def dead: Actor.Receive = {
      case _ =>  
    }
    def receive: Actor.Receive = alive
  }


  val sys = akka.actor.ActorSystem("TicketSys")

  val ticketOffice = sys.actorOf(Props(new SellerActor(100,50)),"MainOffice")

  ticketOffice ! ToSell(75)
  ticketOffice ! Buy(100)
  ticketOffice ! ToSell(75)
  ticketOffice ! Buy(40)
  ticketOffice ! Buy(70)

  ticketOffice ! ToSell(70)
  ticketOffice ! Buy(20)
  ticketOffice ! ToSell(50)
  ticketOffice ! Buy(20)
  ticketOffice ! Buy(15)

  Thread.sleep(1000)

  ticketOffice ! "Bye"
 
  Thread.sleep(10000)
  sys.terminate()
}
