package megvii.testfacepass.custom.db

import android.content.Context
import android.util.Log
import io.objectbox.BoxStore
import io.objectbox.android.AndroidObjectBrowser

object ObjectBox {
    lateinit var boxStore: BoxStore
    private set

    fun init(context: Context) {
        boxStore = MyObjectBox.builder()
                .androidContext(context.applicationContext)
                .build()
    }
}