file://<WORKSPACE>/exercise2/process-simulator/server/src/main/scala/cp/serverSim/Routes.scala
empty definition using pc, found symbol in pc: 
semanticdb not found
empty definition using fallback
non-local guesses:
	 -org/http4s/parsedInstr.
	 -org/http4s/dsl/io/parsedInstr.
	 -java/util/concurrent/atomic/parsedInstr.
	 -parsedInstr.
	 -scala/Predef.parsedInstr.
offset: 3967
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

    // list of dependencies for each instruction
    val dependencies: Array[List[Int]] = Array.fill(parsedInstr.size)(Nil)

    //  num uncompleted dependencies for each instr
    val remainingCounts: Array[Int] = Array.ofDim[Int](parsedInstr.size)

    val done = Array.fill(p@@arsedInstr.size)(new AtomicBoolean(false))
    val remainin = remainingCounts.map(n => new AtomicInteger(n))

    // init structures
    for(i <- parsedInstr.indices){
      val deps = parsedInstr(i).afterDependencies
      remainingCounts(i) = deps.size
      deps.foreach {
        d => dependencies(d) = i :: dependencies(d)
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