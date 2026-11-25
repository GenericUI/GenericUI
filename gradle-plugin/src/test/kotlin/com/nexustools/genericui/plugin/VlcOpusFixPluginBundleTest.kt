package com.nexustools.genericui.plugin

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class VlcOpusFixPluginBundleTest {

    @Test
    fun `VlcNativeBundleTask compiles and stages libgenericui_opus_fix_plugin for available desktop targets`() {
        val tempRoot = Files.createTempDirectory("genericui-opus-fix-bundle-test").toFile()
        try {
            val project = ProjectBuilder.builder().withProjectDir(tempRoot).build()
            val outDir = File(tempRoot, "vlc-bundle")
            val task = project.tasks.register("testBundleVlcOpusFix", VlcNativeBundleTask::class.java) {
                bundleVlcOnDesktop.set(true)
                outputDirectory.set(outDir)
            }.get()

            task.stageVlcRuntimeBinaries()

            val osName = System.getProperty("os.name").orEmpty().lowercase()
            if (osName.contains("mac") || osName.contains("darwin")) {
                val macPlugin = File(outDir, "macos/plugins/${GenericUiVlcOpusFixPluginSource.PLUGIN_STEM}.dylib")
                val macStandalone = File(outDir, "macos/opus_fix_plugins/${GenericUiVlcOpusFixPluginSource.PLUGIN_STEM}.dylib")
                assertTrue(macPlugin.isFile && macPlugin.length() > 0L, "Expected compiled macOS Opus fix plugin in plugins/")
                assertTrue(macStandalone.isFile && macStandalone.length() > 0L, "Expected compiled macOS Opus fix plugin in opus_fix_plugins/")

                val nmOut = ProcessBuilder("nm", "-gU", macPlugin.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                    .let { proc ->
                        val text = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
                        proc.waitFor()
                        text
                    }
                assertTrue("_vlc_entry__3_0_0f" in nmOut, "Expected _vlc_entry__3_0_0f export in macOS plugin:\n$nmOut")
                assertTrue("_vlc_entry_license__3_0_0f" in nmOut, "Expected _vlc_entry_license__3_0_0f export in macOS plugin:\n$nmOut")
            }

            if (VlcNativeBundleTask.canCompileWindowsOpusFixDll()) {
                val winPlugin = File(outDir, "windows/plugins/codec/${GenericUiVlcOpusFixPluginSource.PLUGIN_STEM}.dll")
                val winStandalone = File(outDir, "windows/opus_fix_plugins/${GenericUiVlcOpusFixPluginSource.PLUGIN_STEM}.dll")
                assertTrue(winPlugin.isFile && winPlugin.length() > 0L, "Expected compiled Windows Opus fix DLL in plugins/codec/")
                assertTrue(winStandalone.isFile && winStandalone.length() > 0L, "Expected compiled Windows Opus fix DLL in opus_fix_plugins/")
                val bytes = winPlugin.readBytes()
                assertTrue(bytes.size > 0x100 && bytes[0] == 'M'.code.toByte() && bytes[1] == 'Z'.code.toByte(), "Expected MZ PE header")
                assertTrue("vlc_entry__3_0_0f" in String(bytes, Charsets.ISO_8859_1), "Expected vlc_entry__3_0_0f export in Windows DLL")
            }

            for (linuxName in VlcNativeBundleTask.expectedLinuxOpusFixPluginNames()) {
                val linuxPlugin = File(outDir, "linux/plugins/codec/$linuxName")
                val linuxStandalone = File(outDir, "linux/opus_fix_plugins/$linuxName")
                assertTrue(linuxPlugin.isFile && linuxPlugin.length() > 0L, "Expected compiled Linux Opus fix SO '$linuxName' in plugins/codec/")
                assertTrue(linuxStandalone.isFile && linuxStandalone.length() > 0L, "Expected compiled Linux Opus fix SO '$linuxName' in opus_fix_plugins/")
                val bytes = linuxPlugin.readBytes()
                assertTrue(
                    bytes.size > 64 &&
                        bytes[0] == 0x7F.toByte() &&
                        bytes[1] == 'E'.code.toByte() &&
                        bytes[2] == 'L'.code.toByte() &&
                        bytes[3] == 'F'.code.toByte(),
                    "Expected ELF header for $linuxName"
                )
            }
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
