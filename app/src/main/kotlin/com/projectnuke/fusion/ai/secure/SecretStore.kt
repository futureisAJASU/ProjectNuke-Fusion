package com.projectnuke.fusion.ai.secure

interface SecretStore {
    suspend fun putSecret(id: String, value: String)
    suspend fun getSecret(id: String): String?
    suspend fun deleteSecret(id: String)
}
