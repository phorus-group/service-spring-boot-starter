package group.phorus.service.model

import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import java.io.Serializable
import java.util.*

/**
 * Base class for all JPA entities, providing a [UUID] primary key.
 *
 * This class gives every entity a unique identifier ([id]) of type [UUID], which is automatically
 * generated when the entity is first saved to the database. You never need to set the [id] manually,
 * Hibernate handles it during the insert operation.
 *
 * Extend this class when your entity only needs a unique identifier and does not require automatic
 * audit timestamps (createdAt, updatedAt). For example:
 *
 * ```kotlin
 * @Entity
 * @Table(name = "products")
 * class Product(
 *     @Column(nullable = false)
 *     var name: String? = null,
 *
 *     @Column(nullable = false)
 *     var price: BigDecimal? = null,
 * ) : BaseEntity()
 * ```
 *
 * If your entity needs automatic timestamp tracking for when records are created or modified, extend
 * [AuditableEntity] instead, which includes [id] plus [createdAt][AuditableEntity.createdAt] and [updatedAt][AuditableEntity.updatedAt] fields.
 *
 * The [id] field is nullable (`UUID?`) because:
 * - It is `null` before the entity is saved (new entities don't have an ID yet)
 * - It will become non-null after the first save operation when Hibernate generates the [UUID]
 * - This is a JPA requirement, entities must be instantiable without an ID
 *
 * After saving, the [id] will be guaranteed to be non-null and immutable.
 */
@MappedSuperclass
abstract class BaseEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
) : Serializable