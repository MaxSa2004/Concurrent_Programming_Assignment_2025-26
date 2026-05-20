package cp.serverSim

import scala.collection._

class ThreadPool(val num_threads: Int) {
  private val tasks = mutable.Queue[() => Unit]()

  def execute(task: => Unit): Unit = {
    tasks.synchronized {
      tasks.enqueue(() => task)
      tasks.notify()
    }
  }

  class Worker extends Thread {
    setDaemon(true) 

    def poll() = {
      tasks.synchronized {
        while (tasks.isEmpty) {
          tasks.wait()
        }
        tasks.dequeue()
      }
    } 

    override def run() = {
      while (true) {
        val task = poll()
        task()
      }
    }
  }

  for (_ <- 0 until num_threads) {
    val w = new Worker
    w.start()
  }
}
