package com.example.data.dao

import androidx.room.*
import com.example.data.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT 10")
    fun getRecent10Messages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastModified DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM action_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<LogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: LogEntity): Long

    @Query("DELETE FROM action_logs")
    suspend fun clearLogs()
}

@Dao
interface BuildQueueDao {
    @Query("SELECT * FROM build_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun getPendingQueue(): Flow<List<BuildQueueEntity>>

    @Query("SELECT * FROM build_queue ORDER BY createdAt DESC")
    fun getAllQueue(): Flow<List<BuildQueueEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueue(item: BuildQueueEntity): Long

    @Update
    suspend fun updateQueue(item: BuildQueueEntity)

    @Delete
    suspend fun deleteQueue(item: BuildQueueEntity)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM ai_reviews ORDER BY createdAt DESC")
    fun getAllReviews(): Flow<List<ReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: ReviewEntity): Long
}

@Dao
interface BugScanDao {
    @Query("SELECT * FROM bug_scans WHERE isResolved = 0 ORDER BY timestamp DESC")
    fun getActiveBugs(): Flow<List<BugScanEntity>>

    @Query("SELECT * FROM bug_scans ORDER BY timestamp DESC")
    fun getAllBugs(): Flow<List<BugScanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBugs(bugs: List<BugScanEntity>)

    @Update
    suspend fun updateBug(bug: BugScanEntity)

    @Query("DELETE FROM bug_scans")
    suspend fun clearBugs()
}
