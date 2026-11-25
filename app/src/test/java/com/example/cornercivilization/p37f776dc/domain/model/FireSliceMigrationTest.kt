package com.example.cornercivilization.p37f776dc.domain.model

import org.junit.Assert.*
import org.junit.Test

class FireSliceMigrationTest {
    @Test fun oldSchemasPreserveOnlyTheirExistingFieldEntitlement() {
        for(schema in 1..3) {
            assertFalse(FireSliceMigration.fieldEstablished(schema,0,false))
            assertFalse(FireSliceMigration.fieldEstablished(schema,599_999,true))
            assertTrue(FireSliceMigration.fieldEstablished(schema,600_000,false))
            assertTrue(FireSliceMigration.fieldEstablished(schema,9_000_000,false))
        }
    }
    @Test fun currentSchemaUsesExplicitResultsRegardlessOfClock() {
        for(clock in listOf(0L,599_999L,600_000L,9_000_000L)) {
            assertFalse(FireSliceMigration.fieldEstablished(4,clock,false))
            assertTrue(FireSliceMigration.fieldEstablished(4,clock,true))
        }
    }
    @Test fun newerSchemaAndInvalidClockAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { FireSliceMigration.fieldEstablished(5,0,false) }
        assertThrows(IllegalArgumentException::class.java) { FireSliceMigration.fieldEstablished(0,0,false) }
        assertThrows(IllegalArgumentException::class.java) { FireSliceMigration.fieldEstablished(4,-1,false) }
    }
}
