package com.sksamuel.hoplite

interface Cursor {

  fun value(): Any?

  /**
   * The path in the config to which this cursor points as a list of keys in reverse order (deepest key first).
   */
  fun path(): List<String> = emptyList()

  /**
   * Returns a new [Cursor] which contains the values specified by this cursor, but will fallback to
   * searching the other cursor for paths that do not exist in this cursor.
   */
  fun withFallback(other: Cursor): Cursor = object : Cursor {
    override fun transform(f: (String) -> String) = this@Cursor.transform(f).withFallback(other.transform(f))
    override fun value(): Any? = this@Cursor.value() ?: other.value()
    override fun atKey(key: String): Cursor =
        if (this@Cursor.atKey(key).isUndefined()) other.atKey(key) else this@Cursor.atKey(key)
    override fun isNull(): Boolean = this@Cursor.isNull() || (other.isUndefined() && other.isNull())
    override fun isUndefined(): Boolean = this@Cursor.isUndefined() && other.isUndefined()
  }

  /**
   * The file system location of the config to which this cursor points.
   */
  fun location(): ConfigLocation? = null

  /**
   * Returns whether this cursor points to an undefined value. A cursor can point to an undefined value when a missing
   * config key is requested or when a `null` `ConfigValue` is provided, among other reasons.
   *
   * @return `true` if this cursor points to an undefined value, `false` otherwise.
   */
  fun isUndefined(): Boolean

  /**
   * Returns whether this cursor points to a `null` config value. An explicit `null` value is different than a missing
   * value - `isUndefined` can be used to check for the latter.
   *
   * @return `true` if this cursor points to a `null` value, `false` otherwise.
   */
  fun isNull(): Boolean

  /**
   * Returns a cursor pointing to values stored at the provided key of this cursor.
   */
  fun atKey(key: String): Cursor

  fun transform(f: (String) -> String): Cursor
}

class MapCursor(private val map: Map<*, *>) : Cursor {

  private fun mapValues(f: (String) -> String, m: Map<*, *>): Map<*, *> = map.mapValues {
    when (val value = it.value) {
      is String -> f(value)
      is Map<*, *> -> mapValues(f, value)
      else -> value
    }
  }

  override fun transform(f: (String) -> String): Cursor {
    return MapCursor(mapValues(f, map))
  }

  override fun value(): Any? = map
  override fun path(): List<String> = emptyList()
  override fun location(): ConfigLocation? = null
  override fun isUndefined(): Boolean = false
  override fun isNull(): Boolean = false
  override fun atKey(key: String): Cursor {
    return when (val el = map[key]) {
      null -> UndefinedCursor // ConfigFailure.missingPath(key, map.keys.map { it.toString() }).invalidNel()
      is Map<*, *> -> MapCursor(el)
      else -> PrimitiveCursor(el)
    }
  }
}

class PrimitiveCursor(private val value: Any?) : Cursor {

  override fun transform(f: (String) -> String): Cursor {
    val t = when (value) {
      is String -> f(value)
      else -> value
    }
    return PrimitiveCursor(t)
  }

  override fun isNull(): Boolean = value == null
  override fun isUndefined(): Boolean = false
  override fun value(): Any? = value
  override fun atKey(key: String): Cursor = UndefinedCursor
}

object UndefinedCursor : Cursor {
  override fun transform(f: (String) -> String): Cursor = this
  override fun value(): Any? = null
  override fun isUndefined(): Boolean = true
  override fun isNull(): Boolean = false
  override fun atKey(key: String): Cursor = UndefinedCursor
}