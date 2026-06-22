package com.twobits.pricedrop.data.repository

import com.twobits.pricedrop.data.local.DropDao
import com.twobits.pricedrop.data.model.Drop
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DropsRepository
    @Inject
    constructor(
        private val dropDao: DropDao,
    ) {
        fun observeActiveDrops(): Flow<List<Drop>> = dropDao.observeActive()

        fun observeActiveCount(): Flow<Int> = dropDao.observeActiveCount()

        fun observeDropsForProduct(productId: Long): Flow<List<Drop>> = dropDao.observeForProduct(productId)

        suspend fun addDrop(drop: Drop): Long = dropDao.insert(drop)

        suspend fun dismiss(id: Long) = dropDao.dismiss(id)

        suspend fun dismissAllForProduct(productId: Long) = dropDao.dismissAllForProduct(productId)

        suspend fun dismissAll() = dropDao.dismissAll()
    }
