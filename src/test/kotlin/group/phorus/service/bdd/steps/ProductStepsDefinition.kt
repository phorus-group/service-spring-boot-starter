package group.phorus.service.bdd.steps

import group.phorus.mapper.mapping.extensions.mapTo
import group.phorus.service.bdd.app.dtos.ProductDTO
import group.phorus.service.bdd.app.repositories.ProductRepository
import group.phorus.test.commons.bdd.BaseRequestScenarioScope
import group.phorus.test.commons.bdd.BaseResponseScenarioScope
import group.phorus.test.commons.bdd.BaseScenarioScope
import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.*
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import kotlin.jvm.optionals.getOrNull

class ProductStepsDefinition(
    @Autowired private val baseScenarioScope: BaseScenarioScope,
    @Autowired private val requestScenarioScope: BaseRequestScenarioScope,
    @Autowired private val responseScenarioScope: BaseResponseScenarioScope,
    @Autowired private val productRepository: ProductRepository,
) {
    @Given("the caller has the given Product:")
    fun `the caller has the given Product`(data: DataTable) {
        val product = data.asMaps().first().let { map ->
            ProductDTO(
                name = map["name"]?.takeIf { it.isNotBlank() },
            )
        }
        requestScenarioScope.request = product
    }

    @Then("the Product was created with an uppercased name")
    fun `the Product was created with an uppercased name`() {
        val productId = responseScenarioScope.responseHeaders!!
            .location!!.path
            .replace("/product/", "")
            .let { UUID.fromString(it) }

        val savedProduct = productRepository.findById(productId).getOrNull()?.mapTo<ProductDTO>()

        assertNotNull(savedProduct)
        assertEquals(
            (requestScenarioScope.request as ProductDTO).name?.uppercase(),
            savedProduct!!.name,
            "ProductService.create should have uppercased the name, proving the custom override was used"
        )
    }
}