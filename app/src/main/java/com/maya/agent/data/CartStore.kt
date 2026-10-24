package com.maya.agent.data

import androidx.compose.runtime.mutableStateOf

data class CartDraft(val item: String)

/**
 * Cart-confirm gate (spec section 7): voice/app cart requests land here and
 * NOTHING proceeds until the owner explicitly confirms in the dialog. No
 * payment happens in v3 - confirm only records the accepted order intent.
 */
object CartStore {
    val pending = mutableStateOf<CartDraft?>(null)
    val lastConfirmed = mutableStateOf<CartDraft?>(null)

    fun request(item: String) {
        pending.value = CartDraft(item)
    }

    fun confirm() {
        pending.value?.let { lastConfirmed.value = it }
        pending.value = null
    }

    fun dismiss() {
        pending.value = null
    }
}
