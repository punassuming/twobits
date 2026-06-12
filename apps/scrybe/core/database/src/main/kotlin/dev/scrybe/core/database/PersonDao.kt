package dev.scrybe.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY name")
    fun getAllPersons(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons ORDER BY name")
    suspend fun getAllPersonsOnce(): List<PersonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity)

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun deletePerson(id: String)

    @Query("UPDATE persons SET name = :name WHERE id = :id")
    suspend fun renamePerson(
        id: String,
        name: String,
    )

    @Query("UPDATE speaker_segments SET personId = :targetId WHERE personId = :sourceId")
    suspend fun reassignPersonAcrossSessions(
        sourceId: String,
        targetId: String,
    )

    @Query("SELECT COUNT(DISTINCT sessionId) FROM speaker_segments WHERE personId = :id")
    suspend fun sessionCountForPerson(id: String): Int
}
