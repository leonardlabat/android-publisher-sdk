package com.criteo.publisher.csm

import com.criteo.publisher.csm.MetricObjectQueueFactory.MetricConverter
import com.criteo.publisher.mock.MockedDependenciesRule
import com.criteo.publisher.mock.SpyBean
import com.criteo.publisher.util.BuildConfigWrapper
import com.nhaarman.mockitokotlin2.*
import com.squareup.tape.FileException
import com.squareup.tape.FileObjectQueue
import com.squareup.tape.InMemoryObjectQueue
import com.squareup.tape.ObjectQueue
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import javax.inject.Inject


@RunWith(Parameterized::class)
class TapeMetricSendingQueueTest(private val tapeImplementation: TapeImplementation) {

  @Rule
  @JvmField
  val mockedDependenciesRule = MockedDependenciesRule()

  @Rule
  @JvmField
  val tempFolder = TemporaryFolder()

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{index}: {0}")
    fun data(): Collection<Array<out Any>> {
      return TapeImplementation.values().toList().map { arrayOf(it) }
    }

    enum class TapeImplementation {
      NEW_FILE,
      EMPTY_QUEUE_FILE,
      IN_MEMORY
    }
  }

  @SpyBean
  private lateinit var buildConfigWrapper: BuildConfigWrapper

  @Inject
  private lateinit var metricParser: MetricParser

  private lateinit var tapeQueue: ObjectQueue<Metric>

  private lateinit var queue: TapeMetricSendingQueue

  @Mock
  private lateinit var metricObjectQueueFactory: MetricObjectQueueFactory

  @Before
  fun setUp() {
    MockitoAnnotations.initMocks(this)

    tapeQueue = spy(createObjectQueue())
    doReturn(tapeQueue).whenever(metricObjectQueueFactory).create()
    queue = TapeMetricSendingQueue(metricObjectQueueFactory)
  }

  @Test
  fun getTotalSize_GivenNewQueue_ReturnASmallSize() {
    val size = queue.totalSize

    // The queue needs a little bit of metadata even in case if empty.
    assertThat(size).isLessThan(20)
  }

  @Test
  fun getTotalSize_AfterFewOperations_ReturnSizeGreaterThanEstimation() {
    val estimatedSizePerMetric = 170

    (0 until 1000).forEach {
      queue.offer(mockMetric(it))
    }

    queue.poll(1000)

    (0 until 200).forEach {
      queue.offer(mockMetric(it))
    }

    // Create new instance of TapeMetricSendingQueue to force recreating the queue reference when polling
    // Since the queue is persistent, we should still find the metrics that were previously saved
    queue = TapeMetricSendingQueue(metricObjectQueueFactory)

    val size = queue.totalSize

    if (tapeImplementation == TapeImplementation.IN_MEMORY) {
      assertThat(size).isZero()
    } else {
      assertThat(size).isGreaterThan(estimatedSizePerMetric * 200)
    }
  }

  @Test
  fun offer_GivenAcceptedMetric_ReturnTrue() {
    givenMockedTapeQueue()
    val metric = mockMetric()

    doNothing().whenever(tapeQueue).add(any())

    val isOffered = queue.offer(metric)

    verify(tapeQueue).add(metric)
    assertThat(isOffered).isTrue()
  }

  @Test
  fun offer_GivenExceptionWhileAddingMetric_ReturnFalse() {
    givenDeactivatedPreconditionUtils()
    givenMockedTapeQueue()
    val metric = mockMetric()

    doThrow(FileException::class).whenever(tapeQueue).add(any())

    val isOffered = queue.offer(metric)

    verify(tapeQueue).add(metric)
    assertThat(isOffered).isFalse()
  }

  @Test
  fun poll_AfterAnOfferOperation_ReturnOfferedMetric() {
    val metric = mockMetric()

    queue.offer(metric)
    val metrics = queue.poll(1)

    assertThat(metrics).containsExactly(metric)
  }

  @Test
  fun poll_GivenEmptyQueue_ReturnEmptyList() {
    // given empty queue

    val metrics = queue.poll(10)

    assertThat(metrics).isEmpty()
  }

  @Test
  fun poll_GivenZeroMaxElement_ReturnEmptyList() {
    val metrics = queue.poll(0)

    assertThat(metrics).isEmpty()
    verifyZeroInteractions(tapeQueue)
  }

  @Test
  fun poll_GivenQueueWithEnoughCapacity_ReturnListWithFullSize() {
    val metric1 = mockMetric(1)
    val metric2 = mockMetric(2)

    queue.offer(metric1)
    queue.offer(metric2)

    val metrics = queue.poll(2)

    assertThat(metrics).containsExactly(metric1, metric2)
    assertThat(tapeQueue.size()).isEqualTo(0)
  }

  @Test
  fun poll_GivenQueueInstanceOnWhichOfferWasCalledWasRecreated_ReturnListWithFullSize() {
    val metric1 = mockMetric(1)
    val metric2 = mockMetric(2)

    queue.offer(metric1)
    queue.offer(metric2)

    // Create new instance of TapeMetricSendingQueue to force recreating the queue reference when polling
    // Since the queue is persistent, we should still find the metrics that were previously saved
    queue = TapeMetricSendingQueue(metricObjectQueueFactory)

    val metrics = queue.poll(2)

    assertThat(metrics).containsExactly(metric1, metric2)
    assertThat(tapeQueue.size()).isEqualTo(0)
  }

  @Test
  fun poll_GivenQueueWithNotEnoughCapacity_ReturnListWithOnlyContainedMetrics() {
    val metric1 = mockMetric()

    queue.offer(metric1)

    val metrics = queue.poll(2)

    assertThat(metrics).containsExactly(metric1)
    assertThat(tapeQueue.size()).isEqualTo(0)
  }

  @Test
  fun poll_GivenExceptionWhileReadingFromTape_SilenceTheExceptionAndReturnEmptyList() {
    givenDeactivatedPreconditionUtils()
    doThrow(FileException::class).whenever(tapeQueue).peek()

    val metrics = queue.poll(1)

    assertThat(metrics).isEmpty()
  }

  @Test
  fun poll_GivenExceptionWhileRemovingFromTape_SilenceTheExceptionAndReturnEmptyList() {
    givenMockedTapeQueue()
    doThrow(FileException::class).whenever(tapeQueue).remove()

    val metrics = queue.poll(1)

    assertThat(metrics).isEmpty()
  }

  @Test
  fun poll_GivenManyWorkersInParallel_ShouldNotProduceDuplicate() {
    for (id in 0 until 2000) {
      val metric = mockMetric(id)
      queue.offer(metric)
    }

    val polledMetric = Collections.newSetFromMap(ConcurrentHashMap<Metric, Boolean>())

    val nbWorkers = 10
    val executor = Executors.newFixedThreadPool(nbWorkers)
    val allAreReadyToWork = CyclicBarrier(nbWorkers)
    val allAreDone = CountDownLatch(nbWorkers)

    for (i in 0 until nbWorkers) {
      executor.execute {
        allAreReadyToWork.await()
        val metrics = queue.poll(100)
        polledMetric.addAll(metrics)
        allAreDone.countDown()
      }
    }

    allAreDone.await()

    assertThat(polledMetric).hasSize(100 * nbWorkers)
  }

  private fun createObjectQueue(): ObjectQueue<Metric> {
    return when (tapeImplementation) {
      TapeImplementation.NEW_FILE -> {
        val newFile = tempFolder.newFile()
        newFile.delete()
        FileObjectQueue(newFile, MetricConverter(metricParser))
      }
      TapeImplementation.EMPTY_QUEUE_FILE -> {
        val newFile = tempFolder.newFile()
        newFile.delete()
        FileObjectQueue(newFile, MetricConverter(metricParser))
      }
      TapeImplementation.IN_MEMORY -> {
        InMemoryObjectQueue()
      }
    }
  }

  private fun givenMockedTapeQueue() {
    tapeQueue = mock()
    doReturn(tapeQueue).whenever(metricObjectQueueFactory).create()
    queue = TapeMetricSendingQueue(metricObjectQueueFactory)
  }

  private fun givenDeactivatedPreconditionUtils() {
    buildConfigWrapper.stub {
      on { isDebug } doReturn false
    }
  }

  private fun mockMetric(id: Int = 1): Metric {
    return Metric.builder("id$id")
        .setCdbCallStartTimestamp(42L)
        .setCdbCallEndTimestamp(1337L)
        .setElapsedTimestamp(1024L)
        .build()
  }
}
