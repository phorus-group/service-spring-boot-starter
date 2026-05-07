package group.phorus.service.bdd.steps

import group.phorus.service.bdd.app.dtos.UserDetailResponse
import group.phorus.service.bdd.app.dtos.UserResponse
import group.phorus.service.bdd.app.model.Address
import group.phorus.service.bdd.app.repositories.AddressRepository
import group.phorus.service.bdd.app.repositories.UserRepository
import group.phorus.service.service.fetchAndMapTo
import group.phorus.service.service.transactionalMapTo
import group.phorus.test.commons.bdd.BaseResponseScenarioScope
import group.phorus.test.commons.bdd.BaseScenarioScope
import group.phorus.test.commons.bdd.bodyAs
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.hibernate.stat.Statistics
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.util.*

class TransactionStepsDefinition(
    @Autowired private val baseScenarioScope: BaseScenarioScope,
    @Autowired private val responseScenarioScope: BaseResponseScenarioScope,
    @Autowired private val entityManagerFactory: EntityManagerFactory,
    @Autowired private val transactionTemplate: TransactionTemplate,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val addressRepository: AddressRepository,
    @Autowired private val objectMapper: ObjectMapper,
) {
    private val statistics: Statistics by lazy {
        entityManagerFactory.unwrap(SessionFactory::class.java).statistics.also {
            it.isStatisticsEnabled = true
        }
    }

    @Given("Hibernate statistics are reset")
    fun `Hibernate statistics are reset`() {
        statistics.clear()
    }

    @Given("{int} Addresses exist for the current User")
    fun `N Addresses exist for the current User`(count: Int) {
        val userId = UUID.fromString(baseScenarioScope.objects["userId"] as String)
        val user = userRepository.findById(userId).get()

        repeat(count) { i ->
            addressRepository.saveAndFlush(Address(address = "addr-$i", user = user))
        }
    }

    @Then("at most {int} prepared statements were executed")
    fun `at most N prepared statements were executed`(max: Int) {
        val actual = statistics.prepareStatementCount
        assertTrue(actual <= max,
            "Expected at most $max prepared statements, but $actual were executed"
        )
    }

    @Then("at most {int} Hibernate session(s) was/were opened")
    fun `at most N Hibernate sessions were opened`(max: Int) {
        val actual = statistics.sessionOpenCount
        assertTrue(actual <= max,
            "Expected at most $max Hibernate sessions, but $actual were opened"
        )
    }

    @Then("the User detail response has {int} addresses")
    fun `the User detail response has N addresses`(expected: Int) {
        val response = responseScenarioScope.bodyAs<UserDetailResponse>(objectMapper)!!

        assertNotNull(response.addresses, "addresses must not be null")
        assertEquals(expected, response.addresses!!.size)
    }

    @When("transactionalMapTo is called within an existing read-only transaction")
    fun `transactionalMapTo is called within an existing read-only transaction`() {
        val userId = UUID.fromString(baseScenarioScope.objects["userId"] as String)

        val readOnly = TransactionTemplate(transactionTemplate.transactionManager!!).apply {
            isReadOnly = true
        }

        val result = readOnly.execute {
            val entity = userRepository.findById(userId).get()
            entity.transactionalMapTo<UserDetailResponse>(transactionTemplate)
        }

        baseScenarioScope.objects["mappedResult"] = result!!
    }

    @When("a User is loaded and mapped within a single read-only transaction")
    fun `a User is loaded and mapped within a single read-only transaction`() {
        val userId = UUID.fromString(baseScenarioScope.objects["userId"] as String)

        val readOnly = TransactionTemplate(transactionTemplate.transactionManager!!).apply {
            isReadOnly = true
        }

        val result = readOnly.execute {
            val entity = userRepository.findById(userId).get()
            entity.transactionalMapTo<UserDetailResponse>(transactionTemplate)
        }

        baseScenarioScope.objects["mappedResult"] = result!!
    }

    @Then("the mapped User detail includes addresses")
    fun `the mapped User detail includes addresses`() {
        val result = baseScenarioScope.objects["mappedResult"] as UserDetailResponse

        assertNotNull(result.addresses, "Lazy addresses must be resolved within the joined transaction")
        assertTrue(result.addresses!!.isNotEmpty(), "Addresses must not be empty")
    }

    @When("a detached User is mapped with transactionalMapTo to a DTO without lazy fields")
    fun `a detached User is mapped with transactionalMapTo to a DTO without lazy fields`() {
        val userId = UUID.fromString(baseScenarioScope.objects["userId"] as String)

        val entity = transactionTemplate.execute {
            userRepository.findById(userId).get()
        }!!

        val result = entity.transactionalMapTo<UserResponse>(transactionTemplate)
        baseScenarioScope.objects["mappedResult"] = result!!
    }

    @Then("the mapping succeeds with the correct User data")
    fun `the mapping succeeds with the correct User data`() {
        val result = baseScenarioScope.objects["mappedResult"] as UserResponse

        assertEquals("testUser", result.name)
        assertEquals("sursur", result.surname)
    }

    @When("a detached User is mapped with transactionalMapTo to a DTO with lazy fields")
    fun `a detached User is mapped with transactionalMapTo to a DTO with lazy fields`() {
        val userId = UUID.fromString(baseScenarioScope.objects["userId"] as String)

        val entity = transactionTemplate.execute {
            userRepository.findById(userId).get()
        }!!

        try {
            entity.transactionalMapTo<UserDetailResponse>(transactionTemplate)
        } catch (e: Exception) {
            baseScenarioScope.objects["mappingException"] = e
        }
    }

    @Then("the mapping fails with a lazy initialization error")
    fun `the mapping fails with a lazy initialization error`() {
        val exception = baseScenarioScope.objects["mappingException"]
        assertNotNull(exception, "Expected a LazyInitializationException but no exception was thrown")
    }

    @When("fetchAndMapTo is used to load and map a User")
    fun `fetchAndMapTo is used to load and map a User`() {
        val userId = UUID.fromString(baseScenarioScope.objects["userId"] as String)

        val result = transactionTemplate.fetchAndMapTo<UserDetailResponse> {
            userRepository.findById(userId).get()
        }

        baseScenarioScope.objects["mappedResult"] = result!!
    }
}