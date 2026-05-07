package group.phorus.service.bdd.app.dtos

import group.phorus.mapper.mapping.MapFrom
import group.phorus.service.dtos.validationGroups.Create
import group.phorus.service.dtos.validationGroups.Replace
import group.phorus.service.service.MapTo
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.*

data class AddressDTO (
    @field:NotBlank(groups = [Create::class, Replace::class], message = "Cannot be blank")
    var address: String? = null,

    @MapFrom(["user/id"])
    @MapTo("user")
    @field:NotNull(groups = [Create::class, Replace::class], message = "Cannot be null")
    var userId: UUID? = null,
)

data class AddressResponse(
    var id: UUID? = null,
    var address: String? = null,

    @MapFrom(["user/id"])
    var userId: UUID? = null,
)