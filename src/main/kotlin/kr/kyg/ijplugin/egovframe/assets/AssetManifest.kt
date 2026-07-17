package kr.kyg.ijplugin.egovframe.assets

import com.google.gson.JsonParser

data class AssetEntry(
  val sha256: String,
  val size: Long? = null,
  val upstream: String? = null,
)

data class ZipAsset(
  val sha256: String,
  val size: Long,
  val mediaUrl: String,
  val bundled: Boolean,
)

data class AssetManifest(
  val assets: Map<String, AssetEntry>,
  val zips: Map<String, ZipAsset>,
  val upstreamTag: String,
  val mediaUrlBase: String?,
) {
  companion object {
    val instance: AssetManifest by lazy { load() }

    fun load(): AssetManifest {
      val root = JsonParser.parseString(EgovAssets.resourceText(EgovAssets.MANIFEST)).asJsonObject
      val assets = root.getAsJsonObject("assets")
        ?.entrySet()
        ?.associate { (path, value) ->
          val entry = value.asJsonObject
          path to AssetEntry(
            sha256 = entry["sha256"].asString,
            size = entry["size"]?.asLong,
            upstream = entry["upstream"]?.asString,
          )
        }
        ?: root.getAsJsonObject("bundled").entrySet().associate { (path, value) ->
          path to AssetEntry(sha256 = value.asString)
        }
      val zips = root.getAsJsonObject("zips").entrySet().associate { (name, value) ->
        val entry = value.asJsonObject
        name to ZipAsset(
          sha256 = entry["sha256"].asString,
          size = entry["size"].asLong,
          mediaUrl = (entry["mediaUrl"] ?: entry["url"]).asString,
          bundled = entry["bundled"].asBoolean,
        )
      }
      return AssetManifest(
        assets = assets,
        zips = zips,
        upstreamTag = root["upstreamTag"].asString,
        mediaUrlBase = root["mediaUrlBase"]?.asString ?: zips.values.firstOrNull()?.mediaUrl?.substringBeforeLast('/'),
      )
    }
  }
}
