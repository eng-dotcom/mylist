package com.example.mylist.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 本地 SQLite 数据库管理类，负责维护 `purchased_items` 表并提供 [PurchasedItemDao] 实现。
 */
class AppDatabase private constructor(
  context: Context,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

  private val daoInstance: PurchasedItemDao by lazy { SqlitePurchasedItemDao() }

  fun purchasedItemDao(): PurchasedItemDao = daoInstance

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(CREATE_TABLE_SQL)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    db.execSQL("DROP TABLE IF EXISTS $TABLE_PURCHASED_ITEMS")
    onCreate(db)
  }

  private inner class SqlitePurchasedItemDao : PurchasedItemDao {
    private val mutex = Mutex()
    private val itemsFlow = MutableStateFlow<List<PurchasedItemEntity>>(emptyList())
    private var initialized = false

    override fun observeAllItems(): Flow<List<PurchasedItemEntity>> {
      return itemsFlow.onStart {
        mutex.withLock {
          if (!initialized) {
            itemsFlow.value = queryAllInternal()
            initialized = true
          }
        }
      }
    }

    override suspend fun getItemById(id: Long): PurchasedItemEntity? = mutex.withLock {
      withContext(ioDispatcher) {
        readableDatabase.query(
          TABLE_PURCHASED_ITEMS,
          null,
          "$COL_ID = ?",
          arrayOf(id.toString()),
          null,
          null,
          null
        ).use { cursor ->
          if (cursor.moveToFirst()) cursor.toEntity() else null
        }
      }
    }

    override suspend fun insertItem(item: PurchasedItemEntity): Long = mutex.withLock {
      withContext(ioDispatcher) {
        val values = item.toContentValues(includeId = item.id > 0L)
        val rowId = writableDatabase.insertWithOnConflict(
          TABLE_PURCHASED_ITEMS,
          null,
          values,
          SQLiteDatabase.CONFLICT_REPLACE
        )
        itemsFlow.value = queryAllInternal()
        initialized = true
        rowId
      }
    }

    override suspend fun updateItem(item: PurchasedItemEntity): Int = mutex.withLock {
      withContext(ioDispatcher) {
        val values = item.toContentValues(includeId = false)
        val rows = writableDatabase.update(
          TABLE_PURCHASED_ITEMS,
          values,
          "$COL_ID = ?",
          arrayOf(item.id.toString())
        )
        if (rows > 0) {
          itemsFlow.value = queryAllInternal()
          initialized = true
        }
        rows
      }
    }

    override suspend fun deleteItem(item: PurchasedItemEntity): Int {
      return deleteItemById(item.id)
    }

    override suspend fun deleteItemById(id: Long): Int = mutex.withLock {
      withContext(ioDispatcher) {
        val rows = writableDatabase.delete(
          TABLE_PURCHASED_ITEMS,
          "$COL_ID = ?",
          arrayOf(id.toString())
        )
        if (rows > 0) {
          itemsFlow.value = queryAllInternal()
          initialized = true
        }
        rows
      }
    }

    private suspend fun queryAllInternal(): List<PurchasedItemEntity> = withContext(ioDispatcher) {
      val list = mutableListOf<PurchasedItemEntity>()
      readableDatabase.query(
        TABLE_PURCHASED_ITEMS,
        null,
        null,
        null,
        null,
        null,
        "$COL_EXPIRATION_EPOCH_DAY ASC, $COL_ID ASC"
      ).use { cursor ->
        while (cursor.moveToNext()) {
          list.add(cursor.toEntity())
        }
      }
      list
    }
  }

  companion object {
    const val DATABASE_NAME = "mylist.db"
    const val DATABASE_VERSION = 1
    const val TABLE_PURCHASED_ITEMS = "purchased_items"

    const val COL_ID = "id"
    const val COL_NAME = "name"
    const val COL_QUANTITY = "quantity"
    const val COL_AMOUNT = "amount"
    const val COL_EXPIRATION_EPOCH_DAY = "expirationDateEpochDay"
    const val COL_CREATED_AT = "createdAtEpochMillis"
    const val COL_UPDATED_AT = "updatedAtEpochMillis"

    const val CREATE_TABLE_SQL = """
      CREATE TABLE IF NOT EXISTS purchased_items (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        quantity INTEGER NOT NULL,
        amount REAL,
        expirationDateEpochDay INTEGER NOT NULL,
        createdAtEpochMillis INTEGER NOT NULL,
        updatedAtEpochMillis INTEGER NOT NULL
      )
    """

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: AppDatabase(context).also { INSTANCE = it }
      }
    }

    private fun PurchasedItemEntity.toContentValues(includeId: Boolean): ContentValues {
      return ContentValues().apply {
        if (includeId && id > 0L) {
          put(COL_ID, id)
        }
        put(COL_NAME, name)
        put(COL_QUANTITY, quantity)
        if (amount != null) {
          put(COL_AMOUNT, amount)
        } else {
          putNull(COL_AMOUNT)
        }
        put(COL_EXPIRATION_EPOCH_DAY, expirationDateEpochDay)
        put(COL_CREATED_AT, createdAtEpochMillis)
        put(COL_UPDATED_AT, updatedAtEpochMillis)
      }
    }

    private fun Cursor.toEntity(): PurchasedItemEntity {
      val idIndex = getColumnIndexOrThrow(COL_ID)
      val nameIndex = getColumnIndexOrThrow(COL_NAME)
      val quantityIndex = getColumnIndexOrThrow(COL_QUANTITY)
      val amountIndex = getColumnIndexOrThrow(COL_AMOUNT)
      val expirationIndex = getColumnIndexOrThrow(COL_EXPIRATION_EPOCH_DAY)
      val createdIndex = getColumnIndexOrThrow(COL_CREATED_AT)
      val updatedIndex = getColumnIndexOrThrow(COL_UPDATED_AT)

      return PurchasedItemEntity(
        id = getLong(idIndex),
        name = getString(nameIndex),
        quantity = getInt(quantityIndex),
        amount = if (isNull(amountIndex)) null else getDouble(amountIndex),
        expirationDateEpochDay = getLong(expirationIndex),
        createdAtEpochMillis = getLong(createdIndex),
        updatedAtEpochMillis = getLong(updatedIndex)
      )
    }
  }
}
