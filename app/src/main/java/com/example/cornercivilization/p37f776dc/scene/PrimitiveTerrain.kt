package com.example.cornercivilization.p37f776dc.scene

import org.json.JSONObject
import kotlin.math.floor

/** Actual SGS mesh ray samples, in the unscaled PrimitiveWorld coordinate system. */
internal class PrimitiveTerrain(
    private val columns: Int,
    private val rows: Int,
    private val minX: Float,
    private val minZ: Float,
    private val stepX: Float,
    private val stepZ: Float,
    private val heights: List<Float?>,
    private val water: BooleanArray? = null,
) : PrimitiveGroundSurface {
    init {
        require(columns >= 2 && rows >= 2 && heights.size == columns * rows)
        require(minX.isFinite() && minZ.isFinite())
        require(stepX.isFinite() && stepX > 0f && stepZ.isFinite() && stepZ > 0f)
        require(heights.all { it == null || it.isFinite() })
    }

    private val farShore = IntArray(rows) { row ->
        (columns-1 downTo 0).firstOrNull { water?.get(row*columns+it)==true } ?: -1
    }
    fun beyondRiver(x: Float, z: Float): Boolean {
        val row = kotlin.math.round((z-minZ)/stepZ).toInt()
        if (row !in farShore.indices || farShore[row]<0) return false
        return x > minX + (farShore[row]+1)*stepX && heightOrNull(x,z) != null
    }

    fun isRiver(x:Float,z:Float):Boolean {
        val column=kotlin.math.round((x-minX)/stepX).toInt()
        val row=kotlin.math.round((z-minZ)/stepZ).toInt()
        return column in 0 until columns && row in 0 until rows && water?.get(row*columns+column)==true
    }

    override fun heightAt(x: Float, z: Float): Float {
        require(x.isFinite() && z.isFinite())
        val gridX = (x - minX) / stepX
        val gridZ = (z - minZ) / stepZ
        check(gridX >= 0f && gridX <= columns - 1 && gridZ >= 0f && gridZ <= rows - 1) {
            "Resident route outside sampled terrain: ($x,$z) grid=($gridX,$gridZ)"
        }
        val ix = floor(gridX).toInt().coerceAtMost(columns - 2)
        val iz = floor(gridZ).toInt().coerceAtMost(rows - 2)
        val fx = gridX - ix
        val fz = gridZ - iz
        fun sample(column: Int, row: Int): Float = requireNotNull(heights[row * columns + column]) {
            "Resident route touches water or missing SGS terrain: ($x,$z) cell=($column,$row)"
        }
        val near = sample(ix, iz) * (1f - fx) + sample(ix + 1, iz) * fx
        val far = sample(ix, iz + 1) * (1f - fx) + sample(ix + 1, iz + 1) * fx
        return near * (1f - fz) + far * fz
    }

    /** Same four-corner support rule, without allocating exceptions for river queries. */
    override fun heightOrNull(x:Float,z:Float):Float? {
        if(!x.isFinite()||!z.isFinite())return null
        val gridX=(x-minX)/stepX
        val gridZ=(z-minZ)/stepZ
        if(gridX<0f||gridX>columns-1||gridZ<0f||gridZ>rows-1)return null
        val ix=floor(gridX).toInt().coerceAtMost(columns-2)
        val iz=floor(gridZ).toInt().coerceAtMost(rows-2)
        val a=heights[iz*columns+ix]?:return null
        val b=heights[iz*columns+ix+1]?:return null
        val c=heights[(iz+1)*columns+ix]?:return null
        val d=heights[(iz+1)*columns+ix+1]?:return null
        val fx=gridX-ix
        val fz=gridZ-iz
        val near=a*(1f-fx)+b*fx
        val far=c*(1f-fx)+d*fx
        return near*(1f-fz)+far*fz
    }

    companion object {
        fun fromJson(json: JSONObject): PrimitiveTerrain {
            check(json.getString("coordinateSpace") == "PrimitiveWorld")
            val columns = json.getInt("columns")
            val rows = json.getInt("rows")
            require(columns >= 2 && rows >= 2)
            val stepX = json.getDouble("stepX").toFloat()
            val stepZ = json.getDouble("stepZ").toFloat()
            require(stepX.isFinite() && stepX > 0f && stepZ.isFinite() && stepZ > 0f)
            val minX = json.getDouble("minX").toFloat()
            val minZ = json.getDouble("minZ").toFloat()
            require(minX.isFinite() && minZ.isFinite())
            val values = json.getJSONArray("heights")
            require(values.length() == columns * rows)
            val walkable = json.optJSONArray("walkable")
            require(walkable == null || walkable.length() == values.length())
            val heights = List(values.length()) { index ->
                if (values.isNull(index) || walkable?.getBoolean(index) == false) null
                else values.getDouble(index).toFloat().also { require(it.isFinite()) }
            }
            val water=json.optJSONArray("water")?.let { mask ->
                require(mask.length()==values.length())
                BooleanArray(mask.length(),mask::getBoolean)
            }
            return PrimitiveTerrain(columns, rows, minX, minZ, stepX, stepZ, heights, water)
        }
    }
}
