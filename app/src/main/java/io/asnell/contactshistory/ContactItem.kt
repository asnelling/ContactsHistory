package io.asnell.contactshistory

data class ContactItem(val id: String, val displayName: String) {
    override fun toString(): String = displayName
}