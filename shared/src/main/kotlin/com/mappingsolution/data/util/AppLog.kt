package com.mappingsolution.data.util

/** Platform-neutral logging for shared code; each app installs its own [sink] at startup. */
object AppLog {
    interface Sink {
        fun d(tag: String, message: String)
        fun w(tag: String, message: String, error: Throwable? = null)
    }

    @Volatile
    var sink: Sink = object : Sink {
        override fun d(tag: String, message: String) = println("D/$tag: $message")
        override fun w(tag: String, message: String, error: Throwable?) {
            System.err.println("W/$tag: $message")
            error?.printStackTrace()
        }
    }

    fun d(tag: String, message: String) = sink.d(tag, message)
    fun w(tag: String, message: String, error: Throwable? = null) = sink.w(tag, message, error)
}
