package group.phorus.service.bdd.steps

import group.phorus.mapper.mapping.extensions.mapTo
import group.phorus.mapper.mapping.extensions.updateFrom
import group.phorus.service.bdd.app.dtos.UserDTO
import group.phorus.service.bdd.app.dtos.UserResponse
import group.phorus.service.bdd.app.model.User
import group.phorus.service.bdd.app.repositories.UserRepository
import group.phorus.test.commons.bdd.BaseRequestScenarioScope
import group.phorus.test.commons.bdd.BaseResponseScenarioScope
import group.phorus.test.commons.bdd.BaseScenarioScope
import group.phorus.test.commons.bdd.bodyAs
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import tools.jackson.databind.ObjectMapper
import java.util.*
import kotlin.jvm.optionals.getOrNull


class UserStepsDefinition(
    @Autowired private val baseScenarioScope: BaseScenarioScope,
    @Autowired private val requestScenarioScope: BaseRequestScenarioScope,
    @Autowired private val responseScenarioScope: BaseResponseScenarioScope,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val objectMapper: ObjectMapper,
) {
    @Given("the caller has the given User:")
    fun `the caller has the given User`(data: DataTable) {
        val user = data.asMaps().first().let { map ->
            UserDTO(
                name = map["name"]?.takeIf { value -> value.isNotBlank() },
                surname = map["surname"]?.takeIf { value -> value.isNotBlank() },
            )
        }

        requestScenarioScope.request = user
    }

    @Given("the given User exists:")
    fun `the given User exists`(data: DataTable) {
        val user = data.asMaps().first().let {
            User(
                name = it["name"],
                surname = it["surname"],
            )
        }

        baseScenarioScope.objects["userResponse"] = userRepository.saveAndFlush(user).mapTo<UserResponse>()!!
        baseScenarioScope.objects["userId"] = (baseScenarioScope.objects["userResponse"] as UserResponse).id!!.toString()
    }


    @Then("the new User was created")
    fun `the new User was created`() {
        val userId = responseScenarioScope.responseHeaders!!
            .location!!.path
            .replace("/user/", "")
            .let { UUID.fromString(it) }

        val newUser = userRepository.findById(userId).getOrNull()?.mapTo<UserDTO>()

        assertEquals(requestScenarioScope.request as UserDTO, newUser)
    }

    @Then("the updated User is found in the database")
    fun `the updated user is found in the database`() {
        val oldUser = baseScenarioScope.objects["userResponse"] as UserResponse

        val updatedUser = userRepository.findById(oldUser.id!!).getOrNull()?.mapTo<UserResponse>()
        assertNotEquals(oldUser, updatedUser)

        val expectedUser = oldUser.updateFrom(requestScenarioScope.request!!)
        assertEquals(expectedUser, updatedUser)
    }

    @Then("the replaced User is found in the database")
    fun `the replaced user is found in the database`() {
        val oldUser = baseScenarioScope.objects["userResponse"] as UserResponse
        val requestDto = requestScenarioScope.request as UserDTO

        val replacedUser = userRepository.findById(oldUser.id!!).getOrNull()?.mapTo<UserResponse>()
        assertNotEquals(oldUser, replacedUser)

        // Verify full replacement with all fields provided
        assertEquals(requestDto.name, replacedUser?.name)
        assertEquals(requestDto.surname, replacedUser?.surname)
    }

    @Then("the User was removed from the database")
    fun `the User was removed from the database`() {
        val userId = (baseScenarioScope.objects["userId"] as String).let { UUID.fromString(it) }

        val user = userRepository.findById(userId).getOrNull()

        assertNull(user)
    }

    @Then("the service returns the User")
    fun `the service returns the user`() {
        val userResponse = responseScenarioScope.bodyAs<UserResponse>(objectMapper)!!

        assertEquals(baseScenarioScope.objects["userResponse"] as UserResponse, userResponse)
    }

    @Given("a random non-existent ID is generated")
    fun `a random non-existent ID is generated`() {
        baseScenarioScope.objects["randomId"] = UUID.randomUUID().toString()
    }

    @Then("the User still has all original values")
    fun `the User still has all original values`() {
        val originalUser = baseScenarioScope.objects["userResponse"] as UserResponse
        val currentUser = userRepository.findById(originalUser.id!!).getOrNull()?.mapTo<UserResponse>()
        assertEquals(originalUser, currentUser)
    }

    @Then("the service returns a message with the validation errors")
    fun `the service returns a message with the validation errors`(data: DataTable) {
        val obj = data.asMaps().first()["obj"]!!
        val field = data.asMaps().first()["field"]!!
        val rejectedValue = data.asMaps().first()["rejectedValue"]!!
        val message = data.asMaps().first()["message"]!!

        @Suppress("UNCHECKED_CAST")
        val body = responseScenarioScope.bodyAs<Map<String, Any?>>(objectMapper)
        @Suppress("UNCHECKED_CAST")
        val firstError = (body?.get("validationErrors") as? List<Map<String, Any?>>)?.firstOrNull()

        assertEquals(obj, firstError?.get("obj") as? String)
        assertEquals(field, firstError?.get("field") as? String)
        when (rejectedValue) {
            "null" -> assertNull(firstError?.get("rejectedValue"))
            "blank" -> assertEquals("", firstError?.get("rejectedValue") as? String)
            "[]" -> assertTrue((firstError?.get("rejectedValue") as? List<*>)?.isEmpty() == true)
            else -> assertEquals(rejectedValue, firstError?.get("rejectedValue") as? String)
        }
        assertEquals(message, firstError?.get("message") as? String)
    }
}