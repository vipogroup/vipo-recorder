package com.vipo.recorder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SessionType { SCREEN }
enum class Direction { UNKNOWN }

@Entity(
  tableName = "sessions",
  indices = [
    Index(value = ["startTs"]),
    Index(value = ["type"])
  ]
)
data class SessionEntity(
  @PrimaryKey val sessionId: String,
  val type: String,
  val startTs: Long,
  val endTs: Long?,
  val title: String?,
  val note: String?
)

@Entity(
  tableName = "segments",
  indices = [
    Index(value = ["sessionId"]),
    Index(value = ["startTs"])
  ]
)
data class SegmentEntity(
  @PrimaryKey val segmentId: String,
  val sessionId: String,
  val idx: Int,
  val startTs: Long,
  val endTs: Long?,
  val durationMs: Long?,
  val path: String,
  val lastPackage: String?
)

data class SessionSummary(
  val sessionId: String,
  val startTs: Long,
  val endTs: Long?,
  val segmentCount: Long,
  val totalDurationMs: Long
)
