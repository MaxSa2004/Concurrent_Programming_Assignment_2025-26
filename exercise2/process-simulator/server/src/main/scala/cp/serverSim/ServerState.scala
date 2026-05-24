package cp.serverSim

import java.util.concurrent.atomic._

class ServerState() {
  // TODO: extend the state of the server

  var counter = new AtomicInteger();
  //var counter = 0;

  // ex2.2
  var results = new AtomicReference[List[String]](Nil);

  // 
  def addResult(msg: String): Unit = {
    var update = false;

    while (!update){
      val cur = results.get()

      val newList = msg :: cur

      update = results.compareAndSet(cur, newList)
    }
  }

  def clearResults(): Unit = {
    results.set(Nil)
  }

  
  def toHtml: String = {
    val out = results.get().reverse.mkString("<br>")

    s"""
    <p><strong>counter:</strong> ${counter.get()}</p>
    <hr>
    <p><strong>executed instructions:</strong> $out</p>
    """
  }
  //def toHtml: String =
  // s"<p><strong>counter:</strong> $counter</p>"
}
