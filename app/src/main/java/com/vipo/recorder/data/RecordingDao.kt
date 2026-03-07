package com.vipo.recorder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RecordingDao {

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSession(s: SessionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertSegment(s: SegmentEntity)

  @Query("UPDATE sessions SET endTs = :endTs WHERE sessionId = :sessionId")
  suspend fun closeSession(sessionId: String, endTs: Long)

  @Query("UPDATE segments SET endTs = :endTs, durationMs = :dur WHERE segmentId = :segmentId")
  suspend fun closeSegment(segmentId: String, endTs: Long, dur: Long)

  @Query("SELECT * FROM sessions ORDER BY startTs DESC LIMIT 1")
  suspend fun latestSession(): SessionEntity?

  @Query("SELECT * FROM segments WHERE sessionId = :sessionId ORDER BY idx ASC")
  suspend fun segmentsForSession(sessionId: String): List<SegmentEntity>

  @Query("DELETE FROM segments WHERE sessionId = :sessionId")
  suspend fun deleteSegmentsForSession(sessionId: String)

  @Query("DELETE FROM sessions WHERE sessionId = :sessionId")
  suspend fun deleteSession(sessionId: String)

  @Query(
    """
    SELECT seg.sessionId AS sessionId, seg.path AS path
    FROM segments seg
    WHERE seg.sessionId IN (
      SELECT s.sessionId FROM sessions s WHERE s.startTs BETWEEN :fromTs AND :toTs
    )
    """
  )
  suspend fun segmentFilesAll(fromTs: Long, toTs: Long): List<SegmentFileRow>

  @Query(
    """
    SELECT seg.sessionId AS sessionId, seg.path AS path
    FROM segments seg
    WHERE seg.sessionId IN (
      SELECT s.sessionId
      FROM sessions s
      WHERE s.startTs BETWEEN :fromTs AND :toTs
        AND EXISTS (
          SELECT 1 FROM segments seg2
          WHERE seg2.sessionId = s.sessionId
            AND seg2.lastPackage = :packageName
        )
    )
    """
  )
  suspend fun segmentFilesFiltered(fromTs: Long, toTs: Long, packageName: String): List<SegmentFileRow>

  @Query("SELECT DISTINCT lastPackage FROM segments WHERE lastPackage IS NOT NULL ORDER BY lastPackage ASC")
  suspend fun distinctPackages(): List<String>

  @Query(
    """
    SELECT
      s.sessionId AS sessionId,
      s.startTs AS startTs,
      s.endTs AS endTs,
      COUNT(seg.segmentId) AS segmentCount,
      COALESCE(SUM(COALESCE(seg.durationMs, 0)), 0) AS totalDurationMs
    FROM sessions s
    LEFT JOIN segments seg ON seg.sessionId = s.sessionId
    WHERE s.startTs BETWEEN :fromTs AND :toTs
    GROUP BY s.sessionId
    ORDER BY s.startTs DESC
    """
  )
  suspend fun sessionSummariesAll(fromTs: Long, toTs: Long): List<SessionSummary>

  @Query(
    """
    SELECT
      s.sessionId AS sessionId,
      s.startTs AS startTs,
      s.endTs AS endTs,
      COUNT(seg.segmentId) AS segmentCount,
      COALESCE(SUM(COALESCE(seg.durationMs, 0)), 0) AS totalDurationMs
    FROM sessions s
    LEFT JOIN segments seg ON seg.sessionId = s.sessionId
    WHERE s.startTs BETWEEN :fromTs AND :toTs
      AND EXISTS (
        SELECT 1 FROM segments seg2
        WHERE seg2.sessionId = s.sessionId
          AND seg2.lastPackage = :packageName
      )
    GROUP BY s.sessionId
    ORDER BY s.startTs DESC
    """
  )
  suspend fun sessionSummariesFiltered(fromTs: Long, toTs: Long, packageName: String): List<SessionSummary>
}
