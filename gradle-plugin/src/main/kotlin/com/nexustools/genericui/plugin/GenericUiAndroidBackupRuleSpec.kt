package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Single `<include>` or `<exclude>` rule inside Android `backup_rules.xml` / `data_extraction_rules.xml`.
 */
data class GenericUiAndroidBackupRuleSpec(
    val include: Boolean,
    val domain: String,
    val path: String
) : Serializable
