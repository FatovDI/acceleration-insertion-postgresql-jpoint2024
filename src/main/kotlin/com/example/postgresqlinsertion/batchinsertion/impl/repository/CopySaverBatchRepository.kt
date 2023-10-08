@file:OptIn(DelicateCoroutinesApi::class)

package com.example.postgresqlinsertion.batchinsertion.impl.repository

import com.example.postgresqlinsertion.batchinsertion.api.processor.BatchInsertionByEntityProcessor
import com.example.postgresqlinsertion.batchinsertion.api.saver.BatchInsertionByEntitySaver
import com.example.postgresqlinsertion.batchinsertion.api.saver.BatchInsertionSaver
import com.example.postgresqlinsertion.batchinsertion.exception.BatchInsertionException
import com.example.postgresqlinsertion.batchinsertion.impl.saver.CopyByEntitySaver
import com.example.postgresqlinsertion.logic.entity.BaseEntity
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.datasource.ConnectionHolder
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import javax.sql.DataSource
import kotlin.math.ceil
import kotlin.reflect.KClass

abstract class CopySaverBatchRepository<E : BaseEntity>(
    val entityClass: KClass<E>
) {

    @Value("\${batch_insertion.batch_size}")
    private var batchSize: Int = 100

    @Value("\${batch_insertion.parallel_threads}")
    private var parallelThreads: Int = 1

    @Autowired
    private lateinit var processor: BatchInsertionByEntityProcessor

    @Autowired
    private lateinit var dataSource: DataSource

    private val concurrentSaverHandlerName = "ConcurrentSaverHandler"

    fun saveByCopy(entity: E) {
        getCopySaver().addDataForSave(entity)
    }

    @Suppress("UNCHECKED_CAST")
    fun saveByCopyConcurrent(entity: E) {

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw BatchInsertionException("Transaction is not active. Batch insertion by saver is not available.")
        }

        val handler = TransactionSynchronizationManager.getResource(concurrentSaverHandlerName)
            ?.let { it as ConcurrentSaverHandler<E> }
            ?: let {
                val handler = ConcurrentSaverHandler(processor, entityClass, dataSource, batchSize, parallelThreads)

                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronization {
                        override fun beforeCommit(readOnly: Boolean) {
                            super.beforeCommit(readOnly)
                            handler.commit()
                        }
                        override fun afterCompletion(status: Int) {
                            TransactionSynchronizationManager.unbindResource(concurrentSaverHandlerName)
                        }
                    }
                )

                TransactionSynchronizationManager.bindResource(concurrentSaverHandlerName, handler)
                handler
            }

        handler.addDataForSave(entity)

    }

    @Suppress("UNCHECKED_CAST")
    fun saveAllByCopy(entities: List<E>) {

        val jobsSaveAllWithCoroutineName = "JobsSaveAllWithCoroutine"

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw BatchInsertionException("Transaction is not active. Batch insertion by saver is not available.")
        }

        val jobs = runBlocking {
            entities.chunked(ceil(entities.size.toDouble()/parallelThreads).toInt()).map {
                async(Dispatchers.Default) { saveBatchBySaveAll(it) }
            }
        }.toMutableList()

        TransactionSynchronizationManager.getResource(jobsSaveAllWithCoroutineName)
            ?.let { it as  MutableList<Deferred<BatchInsertionSaver>> }
            ?.let { it.apply { addAll(jobs) } }
            ?: let {
                TransactionSynchronizationManager.bindResource(jobsSaveAllWithCoroutineName, jobs)

                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronization {
                        override fun beforeCommit(readOnly: Boolean) {
                            super.beforeCommit(readOnly)

                            runBlocking {
                                TransactionSynchronizationManager.getResource(jobsSaveAllWithCoroutineName)
                                    ?.let { it as  MutableList<Deferred<BatchInsertionSaver>> }
                                    ?.map { it.await() }
                                    ?.forEach {
                                        it.commit()
                                        it.close()
                                    }
                            }
                        }
                        override fun afterCompletion(status: Int) {
                            TransactionSynchronizationManager.unbindResource(jobsSaveAllWithCoroutineName)
                        }
                    }
                )
            }

    }

    private fun saveBatchBySaveAll(entities: List<E>): BatchInsertionSaver {
        val saver = CopyByEntitySaver(processor, entityClass, dataSource.connection, batchSize)

        entities.forEach { saver.addDataForSave(it) }

        return saver
    }

    @Suppress("UNCHECKED_CAST")
    private fun getCopySaver(): BatchInsertionByEntitySaver<E> {

        val copySaverResourceName = "BatchInsertionCopySaver"

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw BatchInsertionException("Transaction is not active. Batch insertion by saver is not available.")
        }

        return TransactionSynchronizationManager.getResource(copySaverResourceName)
            ?.let { it as BatchInsertionByEntitySaver<E> }
            ?: let {

                val conn = (TransactionSynchronizationManager.getResource(dataSource) as ConnectionHolder).connection

                val saver = CopyByEntitySaver(processor, entityClass, conn, batchSize)

                TransactionSynchronizationManager.registerSynchronization(
                    object : TransactionSynchronization {
                        override fun beforeCommit(readOnly: Boolean) {
                            super.beforeCommit(readOnly)
                            saver.saveData()
                        }
                        override fun afterCompletion(status: Int) {
                            TransactionSynchronizationManager.unbindResource(copySaverResourceName)
                        }
                    }
                )

                TransactionSynchronizationManager.bindResource(copySaverResourceName, saver)
                saver
            }

    }

}