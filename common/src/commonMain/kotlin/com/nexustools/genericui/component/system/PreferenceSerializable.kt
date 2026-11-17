package com.nexustools.genericui.component.system

/**
 * Optional interface that multiplatform classes can implement for explicit, reflection-free
 * field serialization when stored in a [PreferenceStore].
 */
interface PreferenceSerializable {
    /**
     * Returns the ordered map of property names to values representing this object's state.
     */
    fun toPreferenceMap(): Map<String, Any?>

    /**
     * Reconstructs an instance of this type from deserialized [fields] in a reflection-free
     * multiplatform manner, or returns `null` to defer to registered factories/reflector.
     */
    fun fromPreferenceElements(fields: Map<String, PreferenceElement>): Any? = null
}
