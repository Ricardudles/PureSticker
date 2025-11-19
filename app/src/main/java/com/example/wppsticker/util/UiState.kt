package com.example.wppsticker.util

sealed class UiState<out T> {
    object Loading : UiState<Nothing>()
    data class Success<out T>(val data: T) : UiState<T>()
    object Empty : UiState<Nothing>()
}
