package com.google.firebase.example.fireeats.kotlin.model

sealed class Response<out T> {
    class Loading<T> : Response<T>()
    data class Success<out T>(val data: T) : Response<T>()
    data class Failed<T>(val message: String) : Response<T>()

    companion object {
        fun <T> loading() = Loading<T>()
        fun <T> success(data: T) = Success<T>(data)
        fun <T> failed(message: String) = Failed<T>(message)
    }
}
