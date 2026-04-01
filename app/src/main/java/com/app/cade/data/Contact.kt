package com.app.cade.data

data class Contact(
    val id: Int = 0,
    val name: String,
    val phone: String,
    val visualStatus: String,
    val bluetoothAddress: String
)
