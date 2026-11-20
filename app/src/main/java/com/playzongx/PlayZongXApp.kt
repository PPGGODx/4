package com.playzongx

import android.app.Application
import com.playzongx.app.AppContainer

class PlayZongXApp : Application() {

    val container by lazy { AppContainer(this) }
}
