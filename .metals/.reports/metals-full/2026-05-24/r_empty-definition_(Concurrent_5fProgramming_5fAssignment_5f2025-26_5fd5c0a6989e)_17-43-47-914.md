error id: file://<WORKSPACE>/exercise2/process-simulator/server/src/main/scala/cp/serverSim/Routes.scala:println
file://<WORKSPACE>/exercise2/process-simulator/server/src/main/scala/cp/serverSim/Routes.scala
empty definition using pc, found symbol in pc: 
semanticdb not found

found definition using fallback; symbol println
offset: 2321
uri: file://<WORKSPACE>/exercise2/process-simulator/server/src/main/scala/cp/serverSim/Routes.scala
text:
```scala
package cp.serverSim

import cats.effect.IO
import org.http4s._
import org.http4s.dsl.io._
import org.slf4j.LoggerFactory

import java.time.LocalDateTime
import java.time.LocalDateTime

import java.util.concurrent.atomic._

/*
import scala.concurrent._
import java.util.concurrent.ForkJoinPool
*/

// class to store full parsed instruction
private case class Instr(instruction: String, delay: Int, afterDependencies: List[Int])

object Routes {
  // Logger object, printing to the file logs/logs.txt
  private val logger = LoggerFactory.getLogger(getClass)
  private val state = new ServerState()

  // ex2.4 
 private var paused: Boolean = false

  //val num_cores: Int = Runtime.getRuntime().availableProcessors()
  val thread_pool: ThreadPool = new ThreadPool(4)
  /*
  Thread Pool using ExecutionContext
  val pool = new forkjoin.ForkJoinPool(num_cores)
  val ectx = ExecutionContext.fromExecutorService(pool)
  */

  val routes: IO[HttpRoutes[IO]] =
   IO{HttpRoutes.of[IO] {

     // React to a "status" request
     case GET -> Root / "status" =>
       Ok(state.toHtml)
         .map(addCORSHeaders)
         .map(_.withContentType(org.http4s.headers.`Content-Type`(MediaType.text.html)))

     // React to a "reset" request
    case GET -> Root / "reset" =>
      state.counter.getAndSet(0)
      // clear results list on reset
      state.clearResults()
      //state.counter = 0
      Ok("State reset!")
        .map(addCORSHeaders)

     // React to a "run-simulation" request
     case req@GET -> Root / "run-simulation" =>
       val cmdOpt = req.uri.query.params.get("cmd")
       val userIp = req.remoteAddr.getOrElse("unknown")

       //// printing to the terminal instead of a logging file
       //println(">>> got run-simulation!")
       //println(s">>> Cmd: ${cmdOpt}")
       //println(s">>> userIP: $userIp")

       cmdOpt match {
         case Some(cmd) =>
          // calling the `runProcess` method, which simulates running a process
           Ok(runProcess(cmd, userIp.toString))
             .map(addCORSHeaders)

         case None =>
           BadRequest("⚠️ Command not provided. Use /run-simulation?cmd=<your_commands>")
             .map(addCORSHeaders)
       }

      case GET -> Root / "pause" =>
        paused = true
        Ok("SERVER PAUSED").map(addCORSHeaders)
        prin@@tln()
      
      case GET -> Root / "resume" =>
        paused = false
        Ok("SERVER RESUMED").map(addCORSHeaders)
   }}


  /** Run a given process and collect its output. */
  /**
    * This method simulates running a process. It should be replaced with actual code
    * to simulate the process using a thread pool. The `Thread.sleep` is just mimicking
    * the time to process the comand, and should be removed.
    *
    * @param cmd the command to run, which can be a single command or multiple commands separated by ";"
    * @param userIp the IP address of the user who sent the request, used for logging purposes
    * @return a string confirming the received command and user IP, which will be sent back to the client as a response
    */
  private def runProcess(cmd: String, userIp: String): String = {
    val cnt = state.counter.incrementAndGet()
    //state.counter += 1
    //val cnt = state.counter
    
    val cmds = cmd.split(";").map(_.trim).filter(_.nonEmpty)
    // Printing the received command and user IP to the logs
    logger.info(s"🔹 Starting processes (${cnt}) for user $userIp:" +
      s"${cmds.map("\n - "+_).mkString}")

    // TODO:Run process here. The `Thread.sleep` should be removed.

    /*
    // ex2.2 instruction processing
    for(cmd <- cmds){
      val (instruction, delay) = parseIgnoreAfter(cmd)

      thread_pool.execute({
        if (delay > 0){
          Thread.sleep(delay)
        }

        val timestamp = LocalDateTime.now()
        state.addResult(s"[${timestamp}] : ${instruction}")
      })
    }
    */

    // ex2.3
    // parse the instructions
    val parsedInstr = cmds.map(parseAll)

    // dependencies(d) = list of instructions that depend on instruction d
    val dependencies: Array[List[Int]] = Array.fill(parsedInstr.size)(Nil)

    //  remainingCounts(i) = number of deps for instruction i
    val remainingCounts: Array[Int] = Array.ofDim[Int](parsedInstr.size)

    // init structures
    for(i <- parsedInstr.indices){
      val deps = parsedInstr(i).afterDependencies
      remainingCounts(i) = deps.size
      deps.foreach {
        d => dependencies(d) = i :: dependencies(d)
      }
    }

    // atomic thread safe array of if instruction done or not
    val done = Array.fill(parsedInstr.size)(new AtomicBoolean(false))
    // atomic thread safe version of remaingCounts
    val remaining = remainingCounts.map(n => new AtomicInteger(n))

    // schedules instruction i for when it becomes ready and when finished it atomically releases dependants
    // Logic: - remaining(x) is num of unfinished dependencies of instruction x... instruction ready iff remaining(x) == 0
    //        - done(i) ensures that instruction i releases dependants (once only) by decrementing dependents count 
    //          for each dependent j and for each now-ready dependant j we execute it
    //        - the thread doing left == 0 is the one that makes instruction j ready & schedules it
    def executeInstruction(i: Int): Unit = {
      val instruct = parsedInstr(i)

      thread_pool.execute {
        // ex2.4 - if server is paused wait for unpause
        // enter http://localhost:8080/pause in browser to pause
        // enter http://localhost:8080/resume in browser to resume
        while (paused){
          Thread.sleep(1)
        }

        if (instruct.delay > 0){
          Thread.sleep(instruct.delay)
        }

        val timestamp = LocalDateTime.now()
        state.addResult(s"[${timestamp}] : ${instruct.instruction}")

        // change done from false to true for this instruction and trigger
        if(done(i).compareAndSet(false, true)){
          dependencies(i).foreach{j =>
            val left = remaining(j).decrementAndGet()
            if (left == 0){
              executeInstruction(j)
            }
          }
        }
      }
    }

    // start all ready instructions (where dependencies counter = 0)
    parsedInstr.indices.foreach{ i =>
      if (remaining(i).get() == 0){
        executeInstruction(i)
      }
    }


    /*for (cmd <- cmds) {
      val delay: Int = 1000
      thread_pool.execute({
        //Thread.sleep(delay)

        parseIgnoreAfter(cmd)

        //println(s"${Thread.currentThread.getName}: $cmd ${LocalDateTime.now()}")
        //val str_print =  s"$instr Time: ${LocalDateTime.now()}" 
      })
    }*/

    val output: String = s"[${cnt}] Received request from $userIp: ${cmds.mkString(" | ")}"

    output
  }

  // ex2.2 - extracting values from request ignoring after clause
  private def parseIgnoreAfter(rawCommand: String): (String, Int) = {
    // remove comments in command for case of no after clause
    val noComment = rawCommand.split("#", 2).head.trim

    // 2 pieces
    val prefix = noComment.split("\\bafter\\b", 2).head.trim

    val parts = prefix.split("@", 2)
    val instruction = parts(0).trim
    // if no @ then it is 0 delay by default
    val delay = if (parts.length == 2 && parts(1).nonEmpty) parts(1).trim.toInt  else 0

    //println(s"${instruction} with delay: ${delay}")
    (instruction, delay * 1000) // converting seconds to miliseconds for thread.sleep
  }

  // ex2.3 - extracting all values safely
  private def parseAll(rawCommand: String): Instr = {
    val noComment = rawCommand.split("#", 2).head.trim

    val afterSplit = noComment.split("\\bafter\\b", 2).map(_.trim)
    val leftSide = afterSplit(0)
    val afterPart = if (afterSplit.length == 2) Some(afterSplit(1)) else None

    // instructions made to base 0
    val afterDependencies: List[Int] = afterPart match {
      case None => Nil
      case Some(s) => if (s.isEmpty) Nil else s.split(",").toList.map(_.trim).map(_.toInt).map(_ - 1)
    }

    val atSplit = leftSide.split("@", 2)
    val instruction = atSplit(0).trim
    // if no @ then it is 0 delay by default
    val delay = if (atSplit.length == 2 && atSplit(1).nonEmpty) atSplit(1).trim.toInt  else 0

    // converted to seconds
    Instr(instruction = instruction, delay = delay * 1000, afterDependencies = afterDependencies)
  }

  /** Add extra headers, required by the client. */
  def addCORSHeaders(response: Response[IO]): Response[IO] = {
    response.putHeaders(
      "Access-Control-Allow-Origin" -> "*",
      "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
      "Access-Control-Allow-Headers" -> "Content-Type, Authorization",
      "Access-Control-Allow-Credentials" -> "true"
    )
  }
}



```


#### Short summary: 

empty definition using pc, found symbol in pc: 