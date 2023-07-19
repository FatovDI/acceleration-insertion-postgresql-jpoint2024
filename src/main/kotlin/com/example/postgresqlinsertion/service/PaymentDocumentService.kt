package com.example.postgresqlinsertion.service

import com.example.postgresqlinsertion.entity.AccountEntity
import com.example.postgresqlinsertion.entity.CurrencyEntity
import com.example.postgresqlinsertion.entity.PaymentDocumentEntity
import com.example.postgresqlinsertion.repository.AccountRepository
import com.example.postgresqlinsertion.repository.CurrencyRepository
import com.example.postgresqlinsertion.repository.IPaymentDocumentRepository
import com.example.postgresqlinsertion.service.batchinsertion.api.IBatchInsertionFactory
import com.example.postgresqlinsertion.service.batchinsertion.api.ISqlHelper
import com.example.postgresqlinsertion.service.batchinsertion.api.SaverType
import com.example.postgresqlinsertion.utils.getRandomString
import com.example.postgresqlinsertion.utils.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.transaction.Transactional
import kotlin.random.Random

@Service
class PaymentDocumentService(
    @Value("\${spring.jpa.properties.hibernate.jdbc.batch_size}")
    private val batchSize: String,
    private val accountRepo: AccountRepository,
    private val currencyRepo: CurrencyRepository,
    private val paymentDocumentRepo: IPaymentDocumentRepository,
    private val sqlHelper: ISqlHelper,
    private val pdBatchSaverFactory: IBatchInsertionFactory<PaymentDocumentEntity>
) {

    private val log by logger()

    fun saveByCopy(count: Int) {
        val listId = sqlHelper.nextIdList(count)
        val currencies = currencyRepo.findAll()
        val accounts = accountRepo.findAll()
        val bathSizeInt = batchSize.toInt()

        log.info("start collect data for copy saver $count at ${LocalDateTime.now()}")

        pdBatchSaverFactory.getSaver(SaverType.COPY).use { saver ->
            for (i in 0 until count) {
                saver.addDataForSave(getRandom(listId[i], currencies.random(), accounts.random()))
                if (i != 0 && i % bathSizeInt == 0) {
                    log.info("save batch insertion $bathSizeInt by copy method at ${LocalDateTime.now()}")
                    saver.saveData()
                    saver.commit()
                }
            }
            saver.saveData()
            log.info("start last commit data by copy method $count to DB at ${LocalDateTime.now()}")
            saver.commit()
        }


        log.info("end save data by copy method $count at ${LocalDateTime.now()}")

    }

    fun saveByCopyWithTransaction(count: Int) {

        val listId = sqlHelper.nextIdList(count)
        val currencies = currencyRepo.findAll()
        val accounts = accountRepo.findAll()
        val bathSizeInt = batchSize.toInt()

        log.info("start collect data for copy saver with transaction $count at ${LocalDateTime.now()}")

        pdBatchSaverFactory.getSaver(SaverType.COPY).use { saver ->
            for (i in 0 until count) {
                saver.addDataForSave(getRandom(listId[i], currencies.random(), accounts.random()))
                if (i != 0 && i % bathSizeInt == 0) {
                    log.info("save batch insertion $bathSizeInt by copy method with transaction at ${LocalDateTime.now()}")
                    saver.saveData()
                }
            }
            saver.saveData()
            log.info("start commit data by copy method with transaction $count to DB at ${LocalDateTime.now()}")
            saver.commit()
        }

        log.info("end save data by copy method with transaction $count at ${LocalDateTime.now()}")
    }

    fun saveByCopyViaFile(count: Int) {
        val listId = sqlHelper.nextIdList(count)
        val currencies = currencyRepo.findAll()
        val accounts = accountRepo.findAll()

        log.info("start creation file $count at ${LocalDateTime.now()}")

        pdBatchSaverFactory.getSaver(SaverType.COPY_VIA_FILE).use { saver ->
            for (i in 0 until count) {
                saver.addDataForSave(getRandom(listId[i], currencies.random(), accounts.random()))
            }

            log.info("start save file $count to DB at ${LocalDateTime.now()}")

            saver.saveData()
            saver.commit()
        }

        log.info("end save file $count at ${LocalDateTime.now()}")

    }

    fun saveByInsert(count: Int) {
        val listId = sqlHelper.nextIdList(count)
        val currencies = currencyRepo.findAll()
        val accounts = accountRepo.findAll()
        val bathSizeInt = batchSize.toInt()

        log.info("start collect insertion $count at ${LocalDateTime.now()}")

        pdBatchSaverFactory.getSaver(SaverType.INSERT).use { saver ->
            for (i in 0 until count) {
                saver.addDataForSave(getRandom(listId[i], currencies.random(), accounts.random()))
                if (i != 0 && i % bathSizeInt == 0) {
                    log.info("save batch insertion $bathSizeInt at ${LocalDateTime.now()}")
                    saver.saveData()
                    saver.commit()
                }
            }
            saver.saveData()
            log.info("start commit last insert collection $count to DB at ${LocalDateTime.now()}")
            saver.commit()
        }

        log.info("end save insert collection $count at ${LocalDateTime.now()}")

    }

    fun saveByInsertWithTransaction(count: Int) {
        val listId = sqlHelper.nextIdList(count)
        val currencies = currencyRepo.findAll()
        val accounts = accountRepo.findAll()
        val bathSizeInt = batchSize.toInt()

        log.info("start collect insertion $count with transaction at ${LocalDateTime.now()}")

        pdBatchSaverFactory.getSaver(SaverType.INSERT).use { saver ->
            for (i in 0 until count) {
                saver.addDataForSave(getRandom(listId[i], currencies.random(), accounts.random()))
                if (i != 0 && i % bathSizeInt == 0) {
                    log.info("save batch insertion $bathSizeInt with transaction at ${LocalDateTime.now()}")
                    saver.saveData()
                }
            }
            saver.saveData()
            log.info("start commit insert collection $count with transaction at ${LocalDateTime.now()}")
            saver.commit()
        }

        log.info("end save insert collection $count with transaction at ${LocalDateTime.now()}")

    }

    fun saveByInsertWithDropIndex(count: Int) {
        val listId = sqlHelper.nextIdList(count)
        val currencies = currencyRepo.findAll()
        val accounts = accountRepo.findAll()
        val bathSizeInt = batchSize.toInt()

        log.info("start drop index before insertion $count at ${LocalDateTime.now()}")

        val scriptForCreateIndexes = sqlHelper.dropIndex(PaymentDocumentEntity::class)

        log.info("start collect insertion with drop index $count at ${LocalDateTime.now()}")

        pdBatchSaverFactory.getSaver(SaverType.INSERT).use { saver ->
            for (i in 0 until count) {
                saver.addDataForSave(getRandom(listId[i], currencies.random(), accounts.random()))
                if (i != 0 && i % bathSizeInt == 0) {
                    log.info("save batch insertion with drop index $bathSizeInt at ${LocalDateTime.now()}")
                    saver.saveData()
                    saver.commit()
                }
            }

            saver.saveData()
            log.info("start save insert collection with drop index $count to DB at ${LocalDateTime.now()}")
            saver.commit()
        }

        log.info("end save insert collection with drop index $count at ${LocalDateTime.now()}")

        sqlHelper.executeScript(scriptForCreateIndexes)

        log.info("stop drop index after insertion $count at ${LocalDateTime.now()}")

    }

    @Transactional
    fun saveBySpring(count: Int) {
        val currencies = currencyRepo.findAll()
        val accounts = accountRepo.findAll()

        log.info("start save $count via spring at ${LocalDateTime.now()}")

        for (i in 0 until count) {
            paymentDocumentRepo.save(getRandom(null, currencies.random(), accounts.random()))
        }

        log.info("end save $count via spring at ${LocalDateTime.now()}")

    }

    private fun getRandom(id: Long?, cur: CurrencyEntity, account: AccountEntity): PaymentDocumentEntity {
        return PaymentDocumentEntity(
            orderDate = LocalDate.now(),
            orderNumber = getRandomString(10),
            amount = BigDecimal.valueOf(Random.nextDouble()),
            cur = cur,
            expense = Random.nextBoolean(),
            account = account,
            paymentPurpose = getRandomString(100),
            prop10 = getRandomString(10),
            prop15 = getRandomString(15),
            prop20 = getRandomString(20),
        ).apply { this.id = id }
    }
}