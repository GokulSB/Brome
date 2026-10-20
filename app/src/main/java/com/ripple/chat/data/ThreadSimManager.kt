package com.ripple.chat.data

import android.content.Context
import android.content.SharedPreferences

class ThreadSimManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("thread_sim_prefs", Context.MODE_PRIVATE)

    fun getSubIdForThread(threadId: Long): Int? {
        val subId = prefs.getInt("sub_id_$threadId", -1)
        return if (subId >= 0) subId else null
    }

    fun getSlotForThread(threadId: Long): Int? {
        val slot = prefs.getInt("slot_$threadId", -1)
        return if (slot >= 0) slot else null
    }

    fun saveSimForThread(threadId: Long, slot: Int, subId: Int) {
        prefs.edit()
            .putInt("sub_id_$threadId", subId)
            .putInt("slot_$threadId", slot)
            .apply()
    }
}
