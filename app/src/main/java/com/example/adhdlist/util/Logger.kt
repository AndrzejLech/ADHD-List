package com.example.adhdlist.util

import android.util.Log

object Logger {
    fun d(className: String, type: String, message: String ){
        Log.d(className, "$type : $message")
    }

    fun e(className: String, type: String, message: String ){
        Log.e(className, "$type : $message")
    }
}