package com.mappingsolution.data.util

/** Platform-neutral logging for shared code; each app installs its own [sink] at startup. */
object AppLog {
    interface Sink {
        fun d(tag: String, message: String)
        fun i(tag: String, message: String)
        fun w(tag: String, message: String, error: Throwable? = null)
        fun e(tag: String, message: String, error: Throwable? = null)
    }

    @Volatile
    var sink: Sink = object : Sink {
        override fun d(tag: String, message: String) = println("D/$tag: $message")
        override fun i(tag: String, message: String) = println("I/$tag: $message")
        override fun w(tag: String, message: String, error: Throwable?) = err("W", tag, message, error)
        override fun e(tag: String, message: String, error: Throwable?) = err("E", tag, message, error)

        private fun err(level: String, tag: String, message: String, error: Throwable?) {
            System.err.println("$level/$tag: $message")
            error?.printStackTrace()
        }
    }

    fun d(tag: String, message: String) = sink.d(tag, message)
    fun i(tag: String, message: String) = sink.i(tag, message)
    fun w(tag: String, message: String, error: Throwable? = null) = sink.w(tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = sink.e(tag, message, error)
}
