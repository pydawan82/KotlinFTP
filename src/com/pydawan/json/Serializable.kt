package com.pydawan.json

/**
 * This annotation marks a class as suitable for serialization.
 * Properties of this class with [Ignore] annotation will be ignored.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Serializable()
