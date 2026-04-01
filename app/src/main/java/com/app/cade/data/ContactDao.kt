package com.app.cade.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Simplified In-Memory DAO to bypass Room/KAPT Compilation risks on unknown Android SDKs
class ContactDao {
    private val contacts = mutableListOf<Contact>()

    fun getAll(): Flow<List<Contact>> = flow {
        emit(contacts.toList())
    }

    suspend fun insert(contact: Contact) {
        contacts.add(contact.copy(id = contacts.size + 1))
    }

    suspend fun delete(id: Int) {
        contacts.removeAll { it.id == id }
    }
}
