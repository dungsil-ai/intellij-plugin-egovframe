package kr.kyg.intellij.plugin.egovframe.assets

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path

@Service(Service.Level.APP)
class TemplateStoreService {
  val store: TemplateStore = TemplateStore(
    Path.of(PathManager.getSystemPath(), "egovframe-templates"),
    ::fetch,
  )

  private fun fetch(url: String): ByteArray {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
    connection.readTimeout = READ_TIMEOUT_MILLIS
    connection.setRequestProperty("User-Agent", USER_AGENT)
    return try {
      connection.inputStream.use { it.readBytes() }
    } finally {
      connection.disconnect()
    }
  }

  private companion object {
    const val CONNECT_TIMEOUT_MILLIS = 30_000
    const val READ_TIMEOUT_MILLIS = 300_000
    const val USER_AGENT = "intellij-plugin-egovframe/1.0"
  }
}
