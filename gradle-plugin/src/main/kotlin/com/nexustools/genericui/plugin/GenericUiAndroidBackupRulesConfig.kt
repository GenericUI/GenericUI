package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Configuration for Android Auto Backup (`res/xml/backup_rules.xml`) and Android 12+
 * Data Extraction Rules (`res/xml/data_extraction_rules.xml`).
 */
open class GenericUiAndroidBackupRulesConfig : Serializable {
    val cloudRules: MutableList<GenericUiAndroidBackupRuleSpec> = mutableListOf()
    val deviceTransferRules: MutableList<GenericUiAndroidBackupRuleSpec> = mutableListOf()

    /** Includes [domain] / [path] in cloud backup (`fullBackupContent` and `<cloud-backup>`). */
    fun include(domain: String, path: String = ".") {
        addRule(cloudRules, include = true, domain = domain, path = path)
    }

    /** Excludes [domain] / [path] from cloud backup (`fullBackupContent` and `<cloud-backup>`). */
    fun exclude(domain: String, path: String = ".") {
        addRule(cloudRules, include = false, domain = domain, path = path)
    }

    /** Includes [domain] / [path] in Android 12+ `<device-transfer>` rules. */
    fun deviceTransferInclude(domain: String, path: String = ".") {
        addRule(deviceTransferRules, include = true, domain = domain, path = path)
    }

    /** Excludes [domain] / [path] from Android 12+ `<device-transfer>` rules. */
    fun deviceTransferExclude(domain: String, path: String = ".") {
        addRule(deviceTransferRules, include = false, domain = domain, path = path)
    }

    private fun addRule(
        target: MutableList<GenericUiAndroidBackupRuleSpec>,
        include: Boolean,
        domain: String,
        path: String
    ) {
        val trimmedDomain = domain.trim()
        val trimmedPath = path.trim().ifEmpty { "." }
        require(trimmedDomain.isNotEmpty()) { "Backup rule domain must not be blank." }
        val spec = GenericUiAndroidBackupRuleSpec(include = include, domain = trimmedDomain, path = trimmedPath)
        if (spec !in target) {
            target.add(spec)
        }
    }

    /** Returns `true` if any cloud or device-transfer rules are configured. */
    fun isNotEmpty(): Boolean = cloudRules.isNotEmpty() || deviceTransferRules.isNotEmpty()

    /** Merges rules from [overlay] onto a copy of this configuration. */
    fun overlayWith(overlay: GenericUiAndroidBackupRulesConfig): GenericUiAndroidBackupRulesConfig =
        copySpec().apply {
            for (rule in overlay.cloudRules) {
                if (rule !in cloudRules) cloudRules.add(rule.copy())
            }
            for (rule in overlay.deviceTransferRules) {
                if (rule !in deviceTransferRules) deviceTransferRules.add(rule.copy())
            }
        }

    /** Returns an independent deep copy of this backup rules configuration. */
    fun copySpec(): GenericUiAndroidBackupRulesConfig = GenericUiAndroidBackupRulesConfig().also { copy ->
        cloudRules.mapTo(copy.cloudRules) { it.copy() }
        deviceTransferRules.mapTo(copy.deviceTransferRules) { it.copy() }
    }
}
