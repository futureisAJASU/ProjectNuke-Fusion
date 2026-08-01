package com.projectnuke.fusion.ai.secure

interface SecretStore {
    suspend fun putSecret(id: String, value: String): Boolean
    suspend fun getSecret(id: String): String?
    suspend fun deleteSecret(id: String): Boolean
}
