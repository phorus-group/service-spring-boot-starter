package group.phorus.service.model

import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

/**
 * Base class for JPA entities that need automatic timestamp tracking for auditing.
 *
 * This class extends [BaseEntity] (providing the UUID [id] field) and adds two timestamp fields
 * that Hibernate manages automatically: [createdAt] and [updatedAt].
 *
 * In addition to the UUID primary key from [BaseEntity], this class adds:
 * - **createdAt**: set once when the entity is first saved, never changes after that
 * - **updatedAt**: updated automatically every time the entity is modified
 *
 * Both timestamps use [Instant], which stores UTC time with nanosecond precision. This avoids
 * timezone ambiguity and provides accurate, comparable timestamps across servers in different locations.
 *
 * Extend this class when your entity needs to track creation and modification times. Most entities
 * in production systems should use this over plain [BaseEntity].
 *
 * ```kotlin
 * @Entity
 * @Table(name = "orders")
 * class Order(
 *     @Column(nullable = false)
 *     var customerId: UUID? = null,
 *
 *     @Column(nullable = false)
 *     var totalAmount: BigDecimal? = null,
 * ) : AuditableEntity()
 * ```
 *
 * Only use plain [BaseEntity] if you genuinely don't need timestamp tracking.
 *
 * ## How the Timestamps Work
 *
 * Hibernate uses the `@CreationTimestamp` and `@UpdateTimestamp` annotations to automatically
 * populate these fields:
 *
 * - **On insert**: both [createdAt] and [updatedAt] are set to the current UTC time
 * - **On update**: only [updatedAt] is changed to the current UTC time, [createdAt] stays the same
 * - **Column attributes**: [createdAt] is marked `updatable = false` at the database level to prevent
 *   accidental modification
 *
 * You never need to set these fields manually.
 */
@MappedSuperclass
abstract class AuditableEntity(
    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    var createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    @UpdateTimestamp
    var updatedAt: Instant = Instant.now(),
) : BaseEntity()