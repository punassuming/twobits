package com.twobits.pricedrop.data.repository

import com.twobits.pricedrop.data.local.PriceEventDao
import com.twobits.pricedrop.data.local.WatchedProductDao
import com.twobits.pricedrop.data.model.PriceEvent
import com.twobits.pricedrop.data.model.WatchedProduct
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepository @Inject constructor(
    private val watchedProductDao: WatchedProductDao,
    private val priceEventDao: PriceEventDao,
) {
    fun observeAll(): Flow<List<WatchedProduct>> = watchedProductDao.observeAll()

    suspend fun getById(id: Long): WatchedProduct? = watchedProductDao.getById(id)

    suspend fun add(product: WatchedProduct): Long = watchedProductDao.insert(product)

    suspend fun update(product: WatchedProduct) = watchedProductDao.update(product)

    suspend fun remove(id: Long) = watchedProductDao.deactivate(id)

    fun observePriceHistory(productId: Long): Flow<List<PriceEvent>> =
        priceEventDao.observeForProduct(productId)

    suspend fun recordPrice(productId: Long, price: Double, retailer: String) {
        priceEventDao.insert(PriceEvent(productId = productId, price = price, retailer = retailer))
        watchedProductDao.updatePrice(productId, price, System.currentTimeMillis())
    }
}
