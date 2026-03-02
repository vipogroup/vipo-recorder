package com.vipo.recorder.util

import java.util.UUID

object Ids {
  fun sessionId(): String = UUID.randomUUID().toString()
  fun segmentId(): String = UUID.randomUUID().toString()
}
