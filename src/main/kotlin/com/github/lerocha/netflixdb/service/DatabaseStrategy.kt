package com.github.lerocha.netflixdb.service

import com.github.lerocha.netflixdb.entity.AbstractEntity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.naming.PhysicalNamingStrategy

interface DatabaseStrategy {
    fun getPhysicalNamingStrategy(): PhysicalNamingStrategy

    fun getInitDatabase(): String = ""

    fun <T> getSqlValues(vararg properties: T): String

    fun <T : AbstractEntity> getInsertStatement(entity: T): String {
        val properties = getProperties(entity)
        val tableName = getPhysicalNamingStrategy().toPhysicalTableName(Identifier(entity.javaClass.simpleName, false), null).text
        val names = properties.keys.joinToString(", ")
        val values = getSqlValues(*properties.values.toTypedArray())
        return "INSERT INTO $tableName ($names) VALUES ($values);"
    }

    fun <T : AbstractEntity> getInsertStatement(entities: List<T>): String {
        if (entities.isEmpty()) return ""
        val properties = getProperties(entities.first())
        val tableName = getPhysicalNamingStrategy().toPhysicalTableName(Identifier(entities.first().javaClass.simpleName, false), null).text
        val names = properties.keys.joinToString(", ")
        return StringBuilder()
            .appendLine("INSERT INTO $tableName ($names) VALUES")
            .append(
                entities.joinToString(",\n") { entity ->
                    val properties = getProperties(entity)
                    val values = getSqlValues(*properties.values.toTypedArray())
                    "($values)"
                },
            )
            .appendLine(";")
            .toString()
    }

    fun <T : AbstractEntity> getProperties(entity: T): Map<String, Any?> {
        val values =
            (entity.javaClass.superclass.declaredMethods + entity.javaClass.declaredMethods)
                .filter { it.name.startsWith("get") }
                .associate { it.name.lowercase() to it.invoke(entity) }

        return (entity.javaClass.superclass.declaredFields.toList() + entity.javaClass.declaredFields.toList())
            .filter { !it.annotations.any { a -> ignorePropertyAnnotations.contains(a.annotationClass.java) } }
            .filter { values.containsKey("get${it.name.lowercase()}") }
            .associate {
                val idSuffix = if (it.annotations.any { a -> foreignKeyAnnotations.contains(a.annotationClass.java) }) "Id" else ""
                val identifier = Identifier("${it.name}$idSuffix", false)
                val name = getPhysicalNamingStrategy().toPhysicalColumnName(identifier, null).text
                name to values["get${it.name.lowercase()}"]
            }
    }

    companion object {
        val ignorePropertyAnnotations = setOf(OneToMany::class.java, ManyToMany::class.java, GeneratedValue::class.java)
        val foreignKeyAnnotations = setOf(OneToOne::class.java, ManyToOne::class.java)
    }
}
