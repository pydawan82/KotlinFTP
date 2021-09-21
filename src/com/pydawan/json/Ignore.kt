package com.pydawan.json

/**
 * This annotation marks the property as to be ignored for serialization
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
annotation class Ignore()
