package group.phorus.service.bdd.app.model

import group.phorus.service.model.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "products")
class Product(
    @Column(nullable = false)
    var name: String? = null,
) : BaseEntity()