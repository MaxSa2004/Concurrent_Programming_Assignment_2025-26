package cp 

package object SellerActors {
  def log(msg: String): Unit = {
    println(s"${Thread.currentThread.getName}: $msg")
  }
}
