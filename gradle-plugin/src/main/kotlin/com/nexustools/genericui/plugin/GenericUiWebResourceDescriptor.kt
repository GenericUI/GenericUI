package com.nexustools.genericui.plugin

internal data class GenericUiWebResourceDescriptor(
    val id: String,
    val type: String,
    val name: String,
    val path: String,
    val hash: String,
    val mimeType: String,
    val size: Long
)
