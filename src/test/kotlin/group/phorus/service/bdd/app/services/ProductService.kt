package group.phorus.service.bdd.app.services

import group.phorus.service.bdd.app.dtos.ProductDTO
import group.phorus.service.bdd.app.dtos.ProductResponse
import group.phorus.service.bdd.app.model.Product
import group.phorus.service.service.SimpleCrudService
import org.springframework.stereotype.Service
import java.util.*

@Service
class ProductService : SimpleCrudService<Product, ProductDTO, ProductResponse>() {
    override suspend fun create(dto: ProductDTO): UUID {
        dto.name = dto.name?.uppercase()
        return super.create(dto)
    }
}