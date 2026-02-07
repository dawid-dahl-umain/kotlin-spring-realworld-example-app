package io.realworld.model

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
data class Comment(
    var createdAt: OffsetDateTime = OffsetDateTime.now(),
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
    var body: String = "",
    @ManyToOne
    var article: Article = Article(),
    @ManyToOne
    var author: User = User(),
    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    var id: Long = 0,
)
