package com.example.adhdlist.utils

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

// Referenced by testInstrumentationRunner in app/build.gradle.kts; @HiltAndroidTest needs HiltTestApplication.
class CustomTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
