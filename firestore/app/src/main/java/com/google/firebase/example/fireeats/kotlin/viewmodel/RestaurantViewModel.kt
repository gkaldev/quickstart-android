package com.google.firebase.example.fireeats.kotlin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.example.fireeats.kotlin.RestaurantRepository
import com.google.firebase.example.fireeats.kotlin.model.Rating
import com.google.firebase.example.fireeats.kotlin.model.Response
import com.google.firebase.example.fireeats.kotlin.model.Restaurant
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RestaurantViewModel : ViewModel() {
    private val restaurantId: Flow<String>? = null
    private val repository = RestaurantRepository()
    private val _restaurant = MutableStateFlow<Response<Restaurant>>(Response.loading())
    val restaurant: StateFlow<Response<Restaurant>>
        get() = _restaurant

    private val _ratings = MutableStateFlow<Response<List<Rating>>>(Response.loading())
    val ratings: StateFlow<Response<List<Rating>>>
        get() = _ratings


    fun getRestaurant(id: String) {
        viewModelScope.launch {
            launch {
                repository.getRestaurant(id).collect { _restaurant.value = it }
            }
            launch {
                repository.getRatings(id).collect { _ratings.value = it }
            }
        }
    }

}