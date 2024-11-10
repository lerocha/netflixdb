package com.github.lerocha.netflixdb.strategy

import com.github.lerocha.netflixdb.entity.AbstractEntity
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategy

interface DatabaseStrategy {
    fun getPhysicalNamingStrategy(): PhysicalNamingStrategy

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

        val tableName = getPhysicalNamingStrategy().toPhysicalTableName(Identifier(entity.javaClass.simpleName, false), null).text
        val names =
            properties.keys.joinToString(", ") {
                getPhysicalNamingStrategy().toPhysicalColumnName(Identifier(it, false), null).text
            }
        val values = getSqlValues(*properties.values.toTypedArray())
        return "INSERT INTO $tableName ($names) VALUES ($values);"
    }

    fun <T : AbstractEntity> getInsertStatement(entities: List<T>): String {
        TODO("Not yet implemented")
    }
}
