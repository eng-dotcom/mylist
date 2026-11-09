package com.example.mylist

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #1 单元测试：验证 AndroidManifest.xml 已配置锁定竖屏（不自动翻转屏幕）。
 */
class ManifestConfigTest {

  @Test
  fun `AndroidManifest locks MainActivity to portrait orientation`() {
    val candidates = listOf(
      File("src/main/AndroidManifest.xml"),
      File("app/src/main/AndroidManifest.xml")
    )
    val manifestFile = candidates.firstOrNull { it.exists() }
      ?: error("Could not locate AndroidManifest.xml from working directory ${File(".").absolutePath}")

    val content = manifestFile.readText(Charsets.UTF_8)
    assertTrue(
      "MainActivity must declare android:screenOrientation=\"portrait\"",
      content.contains("android:screenOrientation=\"portrait\"")
    )
  }
}

