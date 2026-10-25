package com.github.jengelman.gradle.plugins.shadow.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/**
 * Computes entry bytes in parallel on [Dispatchers.Default] and writes them in submission order,
 * which keeps the output deterministic.
 *
 * Entries written directly bypass the queue, so [flush] must be called before writing them.
 */
internal class OrderedParallelWriter(private val scope: CoroutineScope) {
  private val pendingEntries = ArrayDeque<PendingEntry>()

  fun submit(compute: () -> ByteArray, write: (ByteArray) -> Unit) {
    pendingEntries.addLast(PendingEntry(scope.async(Dispatchers.Default) { compute() }, write))
    if (pendingEntries.size >= MAX_PENDING_ENTRIES) {
      pendingEntries.removeFirst().writeBytes()
    }
  }

  fun flush() {
    while (pendingEntries.isNotEmpty()) {
      pendingEntries.removeFirst().writeBytes()
    }
  }

  private fun PendingEntry.writeBytes() {
    write(runBlocking { deferredBytes.await() })
  }
}

/**
 * Maximum number of in-flight parallel tasks in the sliding window. Bounds memory consumption on
 * large archives while keeping worker threads saturated.
 */
private const val MAX_PENDING_ENTRIES = 128

private class PendingEntry(val deferredBytes: Deferred<ByteArray>, val write: (ByteArray) -> Unit)
