package com.nexustools.genericui.component.fragment

import com.nexustools.genericui.enums.system.AppTarget

/**
 * Marks an [com.nexustools.genericui.component.shell.IApplication] subclass as the custom application
 * class for the specified [targets], discovered by the GenericUI Gradle plugin.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AppApplication(
    val targets: Array<AppTarget> = [AppTarget.COMMON]
)
