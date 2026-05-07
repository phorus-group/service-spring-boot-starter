package group.phorus.service.bdd.app.services.impl

import group.phorus.service.bdd.app.model.Address
import group.phorus.service.bdd.app.repositories.AddressRepository
import group.phorus.service.bdd.app.services.AddressService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.util.*

@Service
class AddressServiceImpl(
    private val addressRepository: AddressRepository,
) : AddressService() {
    override suspend fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Address> =
        withContext(Dispatchers.IO) {
            addressRepository.findAllByUserId(userId, pageable)
        }
}