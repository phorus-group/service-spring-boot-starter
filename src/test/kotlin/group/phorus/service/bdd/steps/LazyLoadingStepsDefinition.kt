package group.phorus.service.bdd.steps

import group.phorus.service.bdd.app.dtos.UserDetailResponse
import group.phorus.test.commons.bdd.BaseResponseScenarioScope
import group.phorus.test.commons.bdd.BaseScenarioScope
import group.phorus.test.commons.bdd.bodyAs
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.ObjectMapper
import java.util.*

class LazyLoadingStepsDefinition(
    @Autowired private val baseScenarioScope: BaseScenarioScope,
    @Autowired private val responseScenarioScope: BaseResponseScenarioScope,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @Then("the User detail response includes the addresses collection")
    fun `the User detail response includes the addresses collection`() {
        val addressId = UUID.fromString(baseScenarioScope.objects["addressId"] as String)

        val response = responseScenarioScope.bodyAs<UserDetailResponse>(objectMapper)!!

        assertNotNull(
            response.addresses,
            "addresses must not be null: the @OneToMany lazy PersistentSet must be initialized " +
                "during mapping within an active Hibernate session"
        )
        assertTrue(
            response.addresses!!.isNotEmpty(),
            "addresses must not be empty: the User has at least one Address"
        )
        assertEquals(
            addressId,
            response.addresses!!.first().id,
            "the address id must match the one created in setup"
        )
    }
}