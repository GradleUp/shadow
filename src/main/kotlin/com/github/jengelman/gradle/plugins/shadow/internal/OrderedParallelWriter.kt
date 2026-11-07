package com.github.jengelman.gradle.plugins.shadow.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * Computes entry bytes in parallel on [Dispatchers.Default] and writes them in submission order,
 * which keeps the output deterministic.
 *
 * Entries that need no computation can be queued with [submitDirect] to keep their position without
 * buffering their content. Entries written outside the writer bypass the queue, so [flush] must be
 * called before writing them.
 */
internal class OrderedParallelWriter(private val scope: CoroutineScope) {
  private val pendingWrites = ArrayDeque<() -> Unit>()

  fun submit(compute: () -> ByteArray, write: (ByteArray) -> Unit) {
    val deferredBytes = scope.async(Dispatchers.Default) { compute() }
    enqueue { write(runBlocking { deferredBytes.await() }) }
  }

  /** Queues [write] in submission order. It runs on the caller thread when its turn comes. */
  fun submitDirect(write: () -> Unit) {
    enqueue(write)
  }

  fun flush() {
    while (pendingWrites.isNotEmpty()) {
      pendingWrites.removeFirst().invoke()
    }
  }

  private fun enqueue(write: () -> Unit) {
    pendingWrites.addLast(write)
    if (pendingWrites.size >= MAX_PENDING_ENTRIES) {
      pendingWrites.removeFirst().invoke()
    }
  }
}

/**
 * Maximum number of in-flight parallel tasks in the sliding window. Bounds memory consumption on
 * large archives while keeping worker threads saturated.
 */
private const val MAX_PENDING_ENTRIES = 128
