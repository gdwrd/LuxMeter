package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.data.dao.CameraSetupDao
import com.example.data.dao.FilmLogDao
import com.example.data.dao.FilmRollDao
import com.example.data.dao.FilmStockDao
import com.example.data.model.CameraSetup
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmRoll
import com.example.data.model.FilmStock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FilmRepository(
  private val filmStockDao: FilmStockDao,
  private val filmLogDao: FilmLogDao,
  private val cameraSetupDao: CameraSetupDao? = null,
  private val filmRollDao: FilmRollDao? = null,
  private val database: AppDatabase? = null
) {
  private val memoryCameraSetups = MutableStateFlow<List<CameraSetup>>(emptyList())
  private val memoryRolls = MutableStateFlow<List<FilmRoll>>(emptyList())
  private val memoryTransaction = Mutex()
  private var nextCameraId = 1

  val allFilmStocks: Flow<List<FilmStock>> = filmStockDao.getAllFilmStocks()
  val allLogEntries: Flow<List<FilmLogEntry>> = filmLogDao.getAllLogEntries()
  val allCameraSetups: Flow<List<CameraSetup>> = cameraSetupDao?.getAllCameraSetups() ?: memoryCameraSetups
  val allRolls: Flow<List<FilmRoll>> = filmRollDao?.getAllRolls() ?: memoryRolls

  suspend fun getCameraSetup(id: Int): CameraSetup? =
    if (cameraSetupDao != null) cameraSetupDao.getCameraSetup(id) else memoryCameraSetups.value.find { it.id == id }

  suspend fun saveCameraSetup(setup: CameraSetup): Long = atomic {
    if (cameraSetupDao != null) cameraSetupDao.saveCameraSetup(setup)
    else {
      val id = if (setup.id == 0) nextCameraId++ else setup.id.also { nextCameraId = maxOf(nextCameraId, it + 1) }
      memoryCameraSetups.value = (memoryCameraSetups.value.filterNot { it.id == id } + setup.copy(id = id))
        .sortedWith(compareBy<CameraSetup> { it.name.lowercase() }.thenBy { it.id })
      id.toLong()
    }
  }

  suspend fun getRoll(id: String): FilmRoll? =
    if (filmRollDao != null) filmRollDao.getRoll(id) else memoryRolls.value.find { it.id == id }

  suspend fun saveRoll(roll: FilmRoll) = atomic { storeRoll(roll) }

  private suspend fun storeRoll(roll: FilmRoll) {
    if (filmRollDao != null) filmRollDao.saveRoll(roll)
    else memoryRolls.value = (memoryRolls.value.filterNot { it.id == roll.id } + roll)
      .sortedWith(compareBy<FilmRoll> { it.isFinished }.thenByDescending { it.createdAt }.thenBy { it.id })
  }

  private suspend fun <T> atomic(block: suspend () -> T): T =
    if (database != null) database.withTransaction { block() } else memoryTransaction.withLock { block() }

  fun getEntriesForRoll(rollId: String): Flow<List<FilmLogEntry>> {
    return filmLogDao.getEntriesForRoll(rollId)
  }

  suspend fun insertStock(stock: FilmStock): Long {
    return filmStockDao.insertFilmStock(stock)
  }

  suspend fun getFilmStock(id: Int): FilmStock? = filmStockDao.getFilmStockById(id)

  suspend fun deleteStock(id: Int) {
    filmStockDao.deleteFilmStockById(id)
  }

  suspend fun insertLogEntry(entry: FilmLogEntry): Long {
    return filmLogDao.insertLogEntry(entry)
  }

  suspend fun insertFrame(entry: FilmLogEntry, updatedRoll: FilmRoll): Long = atomic {
    require(entry.rollId == updatedRoll.id) { "Frame and roll must match" }
    val id = filmLogDao.insertLogEntry(entry)
    storeRoll(updatedRoll)
    id
  }

  suspend fun deleteFrameAndUpdateRoll(id: Int, updatedRoll: FilmRoll) = atomic {
    filmLogDao.deleteEntryById(id)
    storeRoll(updatedRoll)
  }

  suspend fun updateLogNotes(id: Int, notes: String) = atomic { filmLogDao.updateLogNotes(id, notes) }

  suspend fun deleteLogEntry(id: Int) {
    filmLogDao.deleteEntryById(id)
  }

  suspend fun deleteRoll(rollId: String) = atomic {
    filmLogDao.deleteRollEntries(rollId)
    if (filmRollDao != null) filmRollDao.deleteRoll(rollId)
    else memoryRolls.value = memoryRolls.value.filterNot { it.id == rollId }
  }

  suspend fun getLatestEntryForRoll(rollId: String): FilmLogEntry? {
    return filmLogDao.getLatestEntryForRoll(rollId)
  }
}
