package com.google.firebase.example.fireeats.kotlin

import android.util.Log
import com.google.firebase.example.fireeats.kotlin.model.Rating
import com.google.firebase.example.fireeats.kotlin.model.Restaurant
import com.google.firebase.example.fireeats.kotlin.model.Response
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class RestaurantRepository {

    @ExperimentalCoroutinesApi
    fun getRestaurant(id: String) = callbackFlow<Response<Restaurant>> {
        var restaurantRef: DocumentReference? = null
        try {
            restaurantRef = Firebase.firestore.collection("restaurants").document(id)
        } catch(e: Throwable) {
            // If Firebase cannot be initialized, close the stream of data
            // flow consumers will stop collecting and the coroutine will resume
            close(e)
        }
        offer(Response.loading())
        val subscription = restaurantRef?.addSnapshotListener { snapshot, error ->
            if (snapshot == null) {
                offer(Response.failed("No restaurant found"))
                return@addSnapshotListener
            }
            try {
                snapshot.toObject(Restaurant::class.java)?.also {
                    offer(Response.success(it))
                }
            } catch(e: Throwable) {
                offer(Response.failed("Error getting restaurant"))
            }
        }
        awaitClose { subscription?.remove() }
    }

    @ExperimentalCoroutinesApi
    fun getRatings(id: String) = callbackFlow<Response<List<Rating>>> {
        var ratingsQuery: Query? = null
        try {
            ratingsQuery = Firebase.firestore.collection("restaurants").document(id)
                    .collection("ratings")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(50)
        } catch(e: Throwable) {
            // If Firebase cannot be initialized, close the stream of data
            // flow consumers will stop collecting and the coroutine will resume
            offer(Response.failed("Couldn't connect to Firebase"))
            close(e)
        }
        if (ratingsQuery == null) {
            offer(Response.failed("Null ratings query"))
            close()
        }
        offer(Response.loading())
        val subscription = ratingsQuery?.addSnapshotListener { snapshot, error ->
            if (snapshot == null) {
                offer(Response.failed("No ratings found"))
                return@addSnapshotListener
            }
            try {
                snapshot.toObjects(Rating::class.java).also {
                    offer(Response.success(it))
                }
            } catch(e: Throwable) {
                offer(Response.failed("Error getting ratings"))
            }
        }
        awaitClose { subscription?.remove() }
    }
}