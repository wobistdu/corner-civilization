package com.example.cornercivilization.p37f776dc.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class PrimitiveTerrainTest {
    private fun terrain(heights: List<Float?> = listOf(0f, 1f, 2f, 3f)) =
        PrimitiveTerrain(2, 2, 0f, 0f, 1f, 1f, heights)

    @Test fun drinkingWaterUsesRiverMaskRatherThanMissingWalkableGround() {
        val surface=PrimitiveTerrain(2,2,0f,0f,1f,1f,
            listOf(.04f,null,null,.04f),booleanArrayOf(false,true,false,false))
        assertEquals(true,surface.isRiver(1f,0f))
        assertEquals(false,surface.isRiver(0f,1f))
        assertEquals(false,surface.isRiver(-1f,0f))
    }

    @Test fun bilinearHeightUsesRealFourCornerSamples() {
        assertEquals(1.5f, terrain().heightAt(.5f, .5f), .000001f)
        assertEquals(1.25f, terrain().heightAt(.25f, .5f), .000001f)
    }

    @Test fun gridFarCornerRemainsAnExactValidSupport() {
        assertEquals(3f, terrain().heightAt(1f, 1f), .000001f)
        assertEquals(2f, terrain().heightAt(0f, 1f), .000001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingOrWetGridCornerRejectsPathInsteadOfFallingBackToZero() {
        terrain(listOf(0f, null, 2f, 3f)).heightAt(.5f, .5f)
    }

    @Test(expected = IllegalStateException::class)
    fun outsideGridRejectsPathInsteadOfClamping() {
        terrain().heightAt(1.01f, .5f)
    }

    @Test fun nullableQueryPreservesAllFourCornerRequirementsAndEdges() {
        for(mask in 0..15) {
            val surface=terrain(List(4){if(mask and (1 shl it)==0)null else it.toFloat()})
            for(x in listOf(0f,.25f,.5f,1f))for(z in listOf(0f,.25f,.5f,1f)) {
                val expected=if(mask==15)x+2f*z else null
                assertEquals("mask=$mask at=$x,$z",expected,surface.heightOrNull(x,z))
            }
        }
        for(v in listOf(-.0001f,1.0001f,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.MAX_VALUE)) {
            assertNull(terrain().heightOrNull(v,.5f))
            assertNull(terrain().heightOrNull(.5f,v))
        }
    }

    @Test fun nullableQueryMatchesStrictSamplerAcrossUnevenGroundAndWater() {
        val random=kotlin.random.Random(711)
        val surface=PrimitiveTerrain(127,93,-.441f,-.322f,.007f,.007f,
            List(127*93){if(it%13==0||it%127 in 61..65)null else random.nextFloat()*.05f})
        repeat(20000) {
            val x=random.nextFloat()*.91f-.455f
            val z=random.nextFloat()*.67f-.335f
            val expected=try{surface.heightAt(x,z)}
                catch(_:IllegalArgumentException){null} catch(_:IllegalStateException){null}
            assertEquals("at=$x,$z",expected,surface.heightOrNull(x,z))
        }
    }

    @Test fun customSamplerKeepsMissingSupportButDoesNotHideUnrelatedFailures() {
        val sampler=PrimitiveGroundSurface{x,z->
            require(x>=0f);check(z>=0f);x+z
        }
        assertEquals(.75f,sampler.heightOrNull(.5f,.25f))
        assertNull(sampler.heightOrNull(-1f,.25f))
        assertNull(sampler.heightOrNull(.5f,-1f))
        val failure=UnsupportedOperationException("sampler unavailable")
        try {
            PrimitiveGroundSurface{_,_->throw failure}.heightOrNull(0f,0f)
            fail("Unexpected sampler failure must propagate")
        } catch(actual:UnsupportedOperationException) {
            assertSame(failure,actual)
        }
    }
}
