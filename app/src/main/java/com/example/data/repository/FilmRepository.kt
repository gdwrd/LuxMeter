package com.example.data.repository

import com.example.data.dao.FilmLogDao
import com.example.data.dao.FilmStockDao
import com.example.data.model.FilmLogEntry
import com.example.data.model.FilmStock
import kotlinx.coroutines.flow.Flow

class FilmRepository(
  private val filmStockDao: FilmStockDao,
  private val filmLogDao: FilmLogDao
) {
  val allFilmStocks: Flow<List<FilmStock>> = filmStockDao.getAllFilmStocks()
  val allLogEntries: Flow<List<FilmLogEntry>> = filmLogDao.getAllLogEntries()

  fun getEntriesForRoll(rollId: String): Flow<List<FilmLogEntry>> {
    return filmLogDao.getEntriesForRoll(rollId)
  }

  suspend fun insertStock(stock: FilmStock): Long {
    return filmStockDao.insertFilmStock(stock)
  }

  suspend fun deleteStock(id: Int) {
    filmStockDao.deleteFilmStockById(id)
  }

  suspend fun insertLogEntry(entry: FilmLogEntry): Long {
    return filmLogDao.insertLogEntry(entry)
  }

  suspend fun deleteLogEntry(id: Int) {
    filmLogDao.deleteEntryById(id)
  }

  suspend fun deleteRoll(rollId: String) {
    filmLogDao.deleteRollEntries(rollId)
  }

  suspend fun getLatestEntryForRoll(rollId: String): FilmLogEntry? {
    return filmLogDao.getLatestEntryForRoll(rollId)
  }
}
