package edu.cs312.netshift

import android.app.Application

class NetShiftApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
