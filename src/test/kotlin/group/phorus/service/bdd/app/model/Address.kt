package group.phorus.service.bdd.app.model

import group.phorus.service.model.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "addresses")
class Address(
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    var address: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null,
) : BaseEntity()