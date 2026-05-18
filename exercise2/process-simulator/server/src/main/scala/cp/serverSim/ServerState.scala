package cp.serverSim

import java.util.concurrent.atomic._

class ServerState() {
  // TODO: extend the state of the server

  var counter = new AtomicInteger();
  //var counter = 0;

  def toHtml: String =
    s"<p><strong>counter:</strong> ${counter.get()}</p>"
  //def toHtml: String =
  // s"<p><strong>counter:</strong> $counter</p>"
}
