package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Single `<*-path>` entry in `res/xml/file_paths.xml` for AndroidX `FileProvider`.
 */
data class GenericUiAndroidFileProviderPathSpec(
    val tag: String,
    val name: String,
    val path: String
) : Serializable
