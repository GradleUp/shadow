package com.github.jengelman.gradle.plugins.shadow.testkit

@Suppress("UNCHECKED_CAST")
class Arguments private constructor(val list: List<Any?>) {
  operator fun get(index: Int): Any? = list[index]

  operator fun <T> component1(): T = list[0] as T

  operator fun <T> component2(): T = list[1] as T

  operator fun <T> component3(): T = list[2] as T

  operator fun <T> component4(): T = list[3] as T

  operator fun <T> component5(): T = list[4] as T

  operator fun <T> component6(): T = list[5] as T

  operator fun <T> component7(): T = list[6] as T

  operator fun <T> component8(): T = list[7] as T

  operator fun <T> component9(): T = list[8] as T

  operator fun <T> component10(): T = list[9] as T

  override fun toString(): String = list.joinToString(", ", prefix = "Arguments[", postfix = "]")

  companion object {
    fun of(vararg arguments: Any?): Arguments = Arguments(arguments.toList())
  }
}
