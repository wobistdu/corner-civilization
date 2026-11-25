package com.example.cornercivilization.p37f776dc.domain.model

/** Read-time compatibility only; a new save can never gain a field from its process clock. */
internal object FireSliceMigration {
    fun fieldEstablished(schema: Int, evolutionMillis: Long, savedField: Boolean): Boolean {
        require(schema in 1..4)
        require(evolutionMillis >= 0)
        return if (schema < 4) evolutionMillis >= 600_000 else savedField
    }
}
