package group.phorus.service.bdd.app.dtos

import group.phorus.service.dtos.validationGroups.Create
import group.phorus.service.dtos.validationGroups.Replace
import jakarta.validation.constraints.NotBlank
import java.util.*

data class ProductDTO(
    @field:NotBlank(groups = [Create::class, Replace::class], message = "Cannot be blank")
    var name: String? = null,
)

data class ProductResponse(
    var id: UUID? = null,
    var name: String? = null,
)