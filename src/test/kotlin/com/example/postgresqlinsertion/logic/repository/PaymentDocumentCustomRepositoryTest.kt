package com.example.postgresqlinsertion.logic.repository

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner
import java.time.LocalDate

@RunWith(SpringRunner::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(locations = ["classpath:application-test.properties"])
internal class PaymentDocumentCustomRepositoryTest {

    @Autowired
    lateinit var service: PaymentDocumentTestService

    @Test
    fun `save payment document by custom repository test`() {
        val orderNumber = "CR_4"
        val orderDate = LocalDate.now()
        val count = 10

        service.saveByCopyViaSpring(count, orderNumber, orderDate)

        val savedPd = service.findAllByOrderNumberAndOrderDate(orderNumber, orderDate)
        Assertions.assertThat(savedPd.size).isEqualTo(count)
    }

    @Test
    fun `save several payment document by custom repository in one transaction test`() {
        val orderNumber = "CR_5"
        val orderDate = LocalDate.now()
        val count = 10

        service.saveSeveralDataInOneTransaction(count, orderNumber, orderDate)

        val savedPd = service.findAllByOrderNumberAndOrderDate(orderNumber, orderDate)
        Assertions.assertThat(savedPd.size).isEqualTo(count+1)
    }

    @Test
    fun `save several payment document by custom repository in one transaction with rollback test`() {
        val orderNumber = "CR_6"
        val orderDate = LocalDate.now()
        val count = 10

        assertThrows<RuntimeException> {
            service.saveSeveralDataInOneTransactionWithRollback(count, orderNumber, orderDate)
        }
        val savedPd = service.findAllByOrderNumberAndOrderDate(orderNumber, orderDate)
        Assertions.assertThat(savedPd.size).isEqualTo(0)
    }

}
