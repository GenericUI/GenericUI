package com.nexustools.genericui.component.fragment.helper

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import kotlin.reflect.KClass

/**
 * Route registration entry associating a URL/navigation [route] key with a [Fragment] class,
 * optional page [title], and optional [factory].
 */
data class FragmentRouteEntry(
    val route: String,
    val fragmentClass: KClass<out Fragment>,
    val title: String = "",
    val factory: ((FragmentBundle?) -> Fragment)? = null
)
