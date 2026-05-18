package cp.SellerActors

import akka.actor._
import akka.event.Logging

object TicketOfficeTest extends App {
  case class ToSell(n: Int)
  case class Buy(n:Int)

  class SellerActor extends Actor {
    val log = Logging(context.system,this)
    
    var tickets: Int = 0
    def receive: Actor.Receive = {
      case ToSell(n) => 
        tickets += n
      case Buy(n) =>
        if (tickets < n) 
          log.info(s"Could not provide $n tickets, only $tickets available")
        else tickets -= n
      case msg =>
        log.info(s"Main office terminated with $tickets tickets!")
        context.stop(self)
    }
  }

  // Testing the system 

  val sys = akka.actor.ActorSystem("TicketSys")
  val ticketOffice = sys.actorOf(Props[SellerActor],"MainOffice")

  ticketOffice ! ToSell(2000)
  for (x <- 0 until 101) 
    ticketOffice ! Buy(20)
  log("Tried to Buy many ${20*101} tickets.")

  ticketOffice ! ToSell(40)
  ticketOffice ! Buy(30)
  ticketOffice ! ToSell(10)
  ticketOffice ! Buy(30)
  ticketOffice ! Buy(5)

  ticketOffice ! "Bye"
  Thread.sleep(5000)
  sys.terminate()
} 
