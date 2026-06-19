package com.photomap.app

import android.app.Application

class PhotoMapApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
