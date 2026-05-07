package group.phorus.service.bdd.app.repositories

import group.phorus.service.bdd.app.model.Address
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface AddressRepository: JpaRepository<Address, UUID>{
    fun findAllByUserId(userId: UUID, pageable: Pageable): Page<Address>
}