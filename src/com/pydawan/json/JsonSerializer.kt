package com.pydawan.json

import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

private fun isSerializable(o: Any): Boolean {
    return o::class.annotations.map {
        it is Serializable
    }.contains(true)
}

private fun assertSerializable(o: Any) {
    if (!isSerializable(o))
        throw UnsupportedOperationException("${o::class} is not serializable")
}

private fun shouldIgnore(property: KProperty1<out Any, Any?>): Boolean {
    println(property)
    property.annotations.forEach(::println)
    return property.annotations.map {
        it is Ignore
    }.contains(true)
}

fun toJsonObject(o: Any): JSONObject {
    assertSerializable(o)
    return unsafeToJSON(o)
}


private fun unsafeToJSON(o: Any): JSONObject {
    val json = JSONObject()

    o::class.memberProperties.filter { property ->
        !shouldIgnore(property)
    }.forEach {
        json.put(it.name, propertyToJSON(o, it))
    }

    return json
}

private fun propertyToJSON(o: Any, property: KProperty1<out Any, Any?>): Any? {
    val value = property.getter.call(o)
    return dispatch(value)
}

private fun dispatch(value: Any?): Any? {
    return when (value) {
        null -> toJsonNull(value)
        is Number -> toJsonNumber(value)
        is String -> toJsonString(value)
        is Array<*> -> toJsonArray(value)
        else -> toJsonObject(value)
    }
}

private fun toJsonNumber(number: Number): Number {
    return number
}

private fun toJsonString(string: String): String {
    return string
}

private fun toJsonNull(value: Any?): Any? {
    return if (value != null) {
        toJsonObject(value)
    } else
        value
}

private fun toJsonArray(array: Array<*>): JSONArray {
    val json = JSONArray()
    array.map {
        dispatch(it)
    }.forEach(json::put)

    return json
}