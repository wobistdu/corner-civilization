package com.example.cornercivilization.p37f776dc.scene

import com.pico.spatial.core.math.Vector3
import org.json.JSONObject

/** Written from the inspected Editor handoff, never from an old-era game snapshot. */
internal data class PrimitivePreviewContract(
    val bundleUri: String,
    val sceneName: String,
    val residentBundleUri: String,
    val heightfieldAsset: String,
    val residents: List<Resident>,
    val hiddenNodes: List<String> = emptyList(),
) {
    data class Resident(val id: String, val sceneName: String?, val sourceOwnerName: String?, val asset: PrimitiveResidentAssetContract, val route: PrimitiveResidentRoute)

    companion object {
        const val MANIFEST_ASSET = "managed/primitive-preview.json"

        fun fromJson(json: JSONObject): PrimitivePreviewContract {
            require(json.getString("schema") == "cornercivilization.primitive-preview/v1")
            val array = json.getJSONArray("residents")
            val residents = List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val sourceFoot = item.getJSONArray("sourceFootAnchor")
                require(sourceFoot.length() == 3)
                val clipJson = item.getJSONObject("clipNames")
                val gaitJson = item.getJSONObject("authoredMotionMetresPerSecond")
                val contract = PrimitiveResidentAssetContract(
                    sourceFootAnchor = Vector3(sourceFoot.getDouble(0).toFloat(), sourceFoot.getDouble(1).toFloat(), sourceFoot.getDouble(2).toFloat()),
                    sourceForwardYawDegrees = item.getDouble("sourceForwardYawDegrees").toFloat(),
                    targetHeightMetres = item.getDouble("targetHeightMetres").toFloat(),
                    clipNames = PrimitiveActivity.entries.filter { clipJson.has(it.clip) }.associateWith { clipJson.getString(it.clip) },
                    authoredMotionMetresPerSecond = listOf(PrimitiveActivity.WALK, PrimitiveActivity.CARRY).associateWith { gaitJson.getDouble(it.clip).toFloat() },
                    clipRanges = item.optJSONObject("clipRanges")?.let { ranges ->
                        PrimitiveActivity.entries.filter { ranges.has(it.clip) }.associateWith { activity ->
                            val range = ranges.getJSONObject(activity.clip)
                            PrimitiveClipRange(range.getDouble("startSeconds").toFloat(), range.getDouble("endSeconds").toFloat())
                        }
                    } ?: emptyMap(),
                )
                val routeJson = item.getJSONObject("route")
                val stopsJson = routeJson.getJSONArray("stops")
                fun activity(value: String): PrimitiveActivity = requireNotNull(PrimitiveActivity.entries.find { it.clip == value }) { "Unknown resident activity: $value" }
                val stops = List(stopsJson.length()) { stopIndex ->
                    val stop = stopsJson.getJSONObject(stopIndex)
                    PrimitiveRouteStop(
                        PrimitiveGroundPoint(stop.getDouble("x").toFloat(), stop.getDouble("z").toFloat()),
                        stop.getDouble("dwellSeconds").toFloat(), activity(stop.getString("arrivalActivity")), activity(stop.getString("departingActivity")),
                    )
                }
                val sourceOwner = if (item.has("sourceOwnerName")) item.getString("sourceOwnerName") else null
                val sceneName = if (item.has("sceneName") && !item.isNull("sceneName")) item.getString("sceneName") else null
                require((sourceOwner == null) != (sceneName == null)) { "Resident requires exactly one actual source owner or bundle scene" }
                Resident(item.getString("id"), sceneName, sourceOwner, contract,
                    PrimitiveResidentRoute(stops, routeJson.getDouble("speedMetresPerSecond").toFloat(), routeJson.getDouble("phaseSeconds").toFloat()))
            }
            require(residents.size in 8..10 && residents.map { it.id }.distinct().size == residents.size)
            val sourceOwners = residents.mapNotNull { it.sourceOwnerName }
            require(sourceOwners.distinct().size == sourceOwners.size) { "Each imported resident owner can be transferred only once" }
            val bundle = json.getString("bundleUri")
            require(bundle.startsWith("asset://managed/")) { "Preview requires an inspected managed Editor bundle" }
            val residentBundle = json.optString("residentBundleUri", bundle)
            require(residentBundle.startsWith("asset://managed/"))
            val hiddenJson = json.optJSONArray("hiddenNodes")
            val hiddenNodes = if (hiddenJson == null) emptyList() else List(hiddenJson.length()) { hiddenJson.getString(it) }
            return PrimitivePreviewContract(bundle, json.getString("sceneName"), residentBundle, json.getString("heightfieldAsset"), residents, hiddenNodes)
        }
    }
}
