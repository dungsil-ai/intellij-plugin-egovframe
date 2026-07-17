package kr.kyg.ijplugin.egovframe.assets

/** Immutable access to the upstream assets bundled under `resources/egovframe/`. */
object EgovAssets {

  const val PROJECT_CATALOG = "egovframe/templates-projects.json"
  const val CONFIG_CATALOG = "egovframe/templates-context-xml.json"
  const val CODE_DIR = "egovframe/code"
  const val CONFIG_DIR = "egovframe/config"
  const val POM_DIR = "egovframe/projects/pom"
  const val EXAMPLES_DIR = "egovframe/projects/examples"
  const val MANIFEST = "egovframe/asset-manifest.json"

  fun resourceBytes(path: String): ByteArray {
    val stream = EgovAssets::class.java.classLoader.getResourceAsStream(path)
      ?: throw IllegalStateException("Bundled resource not found: $path")
    return stream.use { it.readBytes() }
  }

  fun resourceText(path: String): String =
    resourceBytes(path).toString(Charsets.UTF_8)
}
