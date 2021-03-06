package com.holdenkarau.spark.testing

import org.apache.spark.streaming.TestStreamingContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.util.TestManualClock

import scala.reflect.ClassTag

/**
 * Methods for testing Spark actions.   Because actions don't return a DStream, you will need
 * to verify the results of your test against mocks.
 */
trait StreamingActionBase extends StreamingSuiteBase {

  /**
   * Execute unary DStream operation with a list of inputs and no expected output
   * @param input      Sequence of input collections
   * @param operation  Binary DStream operation to be applied to the 2 inputs
   */
  def runAction[U: ClassTag](
                              input: Seq[Seq[U]],
                              operation: DStream[U] => Unit
                              ) {
    val numBatches_ = input.size
    val output =
      withStreamingContext(setupStream[U](input, operation)) { ssc =>
        runActionStream(ssc, numBatches_)
      }
  }

  def withStreamingContext(outputStreamSSC: TestStreamingContext)
                          (block: TestStreamingContext => Unit): Unit = {
    try {
      block(outputStreamSSC)
    } finally {
      try {
        outputStreamSSC.stop(stopSparkContext = false)
      } catch {
        case e: Exception =>
          logError("Error stopping StreamingContext", e)
      }
    }
  }

  def setupStream[U: ClassTag](
                                input: Seq[Seq[U]],
                                operation: DStream[U] => Any
                                ): TestStreamingContext = {

    // Create TestStreamingContext
    val ssc = new TestStreamingContext(sc, batchDuration)
    if (checkpointDir != null) {
      ssc.checkpoint(checkpointDir)
    }

    // Setup the stream computation
    val inputStream = createTestInputStream(sc, ssc, input)
    operation(inputStream)
    ssc
  }

  def runActionStream(
                       ssc: TestStreamingContext,
                       numBatches: Int
                       ) {
    assert(numBatches > 0, "Number of batches to run stream computation is zero")

    try {
      // Start computation
      ssc.start()

      // Advance manual clock
      val clock = ssc.getScheduler().clock.asInstanceOf[TestManualClock]
      logInfo("Manual clock before advancing = " + clock.currentTime())
      if (actuallyWait) {
        for (i <- 1 to numBatches) {
          logInfo("Actually waiting for " + batchDuration)
          clock.addToTime(batchDuration.milliseconds)
          Thread.sleep(batchDuration.milliseconds)
        }
      } else {
        clock.addToTime(numBatches * batchDuration.milliseconds)
      }
      logInfo("Manual clock after advancing = " + clock.currentTime())

      ssc.awaitTerminationOrTimeout(100)

      Thread.sleep(100) // Give some time for the forgetting old RDDs to complete
    } finally {
      ssc.stop(stopSparkContext = false)
    }
  }

}

