package io.asnell.contactshistory

import android.net.Uri

data class ContactItem(val id: String, val displayName: String, val changed: String, val lookupUri: Uri) {
    override fun toString(): String = displayName
}