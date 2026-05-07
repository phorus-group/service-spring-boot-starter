package group.phorus.service.bdd.app.dtos

import group.phorus.service.dtos.validationGroups.Create
import group.phorus.service.dtos.validationGroups.Replace
import jakarta.validation.constraints.NotBlank
import java.util.*

data class UserDTO (
    @field:NotBlank(groups = [Create::class, Replace::class], message = "Cannot be blank")
    var name: String? = null,

    @field:NotBlank(groups = [Create::class, Replace::class], message = "Cannot be blank")
    var surname: String? = null,
)

data class UserResponse(
    var id: UUID? = null,
    var name: String? = null,
    var surname: String? = null,
)

data class UserDetailResponse(
    var id: UUID? = null,
    var name: String? = null,
    var surname: String? = null,
    var addresses: List<AddressResponse>? = null,
)