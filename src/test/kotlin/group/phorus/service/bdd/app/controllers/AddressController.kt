package group.phorus.service.bdd.app.controllers

import group.phorus.mapper.mapping.extensions.mapTo
import group.phorus.service.bdd.app.dtos.AddressResponse
import group.phorus.service.bdd.app.services.AddressService
import group.phorus.service.controller.SimpleCrudController
import group.phorus.service.bdd.app.dtos.AddressDTO
import group.phorus.service.bdd.app.model.Address
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/address")
class AddressController(
    private val addressService: AddressService,
) : SimpleCrudController<Address, AddressDTO, AddressResponse>() {
    @GetMapping("/findAllBy/userId")
    @ResponseStatus(HttpStatus.OK)
    suspend fun findAllByUserId(
        @RequestParam
        userId: UUID,

        @PageableDefault
        pageable: Pageable,
    ): Page<AddressResponse> =
        addressService.findAllByUserId(userId, pageable).let { page ->
            PageImpl(page.mapTo<List<AddressResponse>>()!!, pageable, page.totalElements)
        }
}