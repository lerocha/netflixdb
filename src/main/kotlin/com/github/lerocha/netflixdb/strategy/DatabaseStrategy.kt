package com.github.lerocha.netflixdb.strategy

import com.github.lerocha.netflixdb.entity.AbstractEntity
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne

interface DatabaseStrategy {
    fun getName(name: String): String

    fun <T> getSqlValues(vararg properties: T): String

    fun <T : AbstractEntity> getInsertStatement(entity: T): String {
        val relationshipAnnotations =
            setOf(
                OneToOne::class.java,
                OneToMany::class.java,
                ManyToOne::class.java,
                ManyToMany::class.java,
            )

        val methods =
            (entity.javaClass.superclass.declaredMethods + entity.javaClass.declaredMethods)
                .filter { it.name.startsWith("get") }
                .associate { it.name.lowercase() to it.invoke(entity) }

        val properties =
            (entity.javaClass.superclass.declaredFields.toList() + entity.javaClass.declaredFields.toList())
                .filter { it.annotations.any { a -> !relationshipAnnotations.contains(a.annotationClass.java) } }
                .filter { methods.containsKey("get${it.name.lowercase()}") }
                .associate { it.name to methods["get${it.name.lowercase()}"] }

        val tableName = getName(entity.javaClass.simpleName)
        val names = properties.keys.joinToString(", ") { getName(it) }
        val values = getSqlValues(*properties.values.toTypedArray())
        return "INSERT INTO $tableName ($names) VALUES ($values);"
    }

    fun <T : AbstractEntity> getInsertStatement(entities: List<T>): String {
        TODO("Not yet implemented")
    }
}
