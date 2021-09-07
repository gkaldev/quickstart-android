package com.google.firebase.example.fireeats.kotlin

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberImagePainter
import com.google.android.gms.tasks.Task
import com.google.android.material.composethemeadapter.MdcTheme
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.example.fireeats.databinding.FragmentRestaurantDetailBinding
import com.google.firebase.example.fireeats.kotlin.model.Rating
import com.google.firebase.example.fireeats.kotlin.model.Response
import com.google.firebase.example.fireeats.kotlin.model.Restaurant
import com.google.firebase.example.fireeats.kotlin.util.RatingUtil.toSimpleString
import com.google.firebase.example.fireeats.kotlin.viewmodel.RestaurantViewModel
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import me.zhanghai.android.materialratingbar.MaterialRatingBar

@Composable
fun Ratings(ratingsFlow: StateFlow<Response<List<Rating>>>) {
    val ratings by ratingsFlow.collectAsState()
    when (val _ratings = ratings) {
        is Response.Success -> RatingList(_ratings.data)
        is Response.Failed -> Text(text = "Failed: ${_ratings.message}")
        is Response.Loading -> Text(text = "Loading...")
    }
}

@Composable
fun RatingList(ratings: List<Rating>) {
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(items = ratings) { rating ->
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Row() {
                    Text(text = rating.userName ?: "Unknown User",
                            style = MaterialTheme.typography.overline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    rating.timestamp?.let {
                        Text(text = it.toSimpleString(),
                                style = MaterialTheme.typography.overline
                        )
                    }
                    Stars(rating.rating.toFloat(), modifier = Modifier.height(10.dp))
                }
                Row() {
                    Text(text = rating.text ?: "No text",
                            style = MaterialTheme.typography.body2)
                }

            }
        }
    }
}

@Composable
fun RestaurantScreen(viewModel: RestaurantViewModel) {
    Column() {
        RestaurantHeader(restaurantFlow = viewModel.restaurant, modifier = Modifier.height(200.dp))
        Ratings(ratingsFlow = viewModel.ratings)
    }
}

@Composable
fun RestaurantHeader(restaurantFlow: StateFlow<Response<Restaurant>>, modifier: Modifier) {
    val restaurant by restaurantFlow.collectAsState()
    Surface(color = MaterialTheme.colors.secondary, modifier = modifier) {
        when(val _restaurant = restaurant) {
            is Response.Failed -> Text(text = "Failed")
            is Response.Loading -> Text(text = "Loading")
            is Response.Success -> RestaurantDetails(_restaurant.data)
        }
    }
}

@ExperimentalCoilApi
@Composable
fun RestaurantDetails(restaurant: Restaurant) {
    Box(modifier = Modifier.height(200.dp)) {
        Image(
                painter = rememberImagePainter(restaurant.photo),
                contentDescription = "Restaurant Image",
                modifier = Modifier.fillMaxSize()
        )
        Column {
            restaurant.name?.let {
                Row() {
                    Text(text = it, style = MaterialTheme.typography.h5.copy(color = Color.White), modifier = Modifier.padding(4.dp))
                    Text(text = "$".repeat(restaurant.price),
                            style = MaterialTheme.typography.h6.copy(color = Color.White), modifier = Modifier.padding(4.dp))
                }
            }
            Row() {
                Stars(restaurant.avgRating.toFloat(), modifier = Modifier.height(20.dp))
                Text(text = "(${restaurant.numRatings})",
                        style = MaterialTheme.typography.subtitle1.copy(color = Color.White))
            }
            Row() {
                restaurant.category?.let {
                    Text(text = it, style = MaterialTheme.typography.subtitle1.copy(color = Color.White))
                }
                Spacer(modifier = Modifier.padding(4.dp))
                restaurant.city?.let {
                    Text(text = it, style = MaterialTheme.typography.subtitle1.copy(color = Color.White))
                }
            }
        }

    }
}

@Composable
fun Stars(rating: Float, modifier: Modifier = Modifier) {
    AndroidView(modifier = modifier,
            factory = { context ->
                MaterialRatingBar(context).apply { } },
            update = { view ->
                view.rating = rating
            })

}

class RestaurantDetailFragment : Fragment(),
        RatingDialogFragment.RatingListener {

    private var ratingDialog: RatingDialogFragment? = null

    private lateinit var binding: FragmentRestaurantDetailBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var restaurantRef: DocumentReference

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentRestaurantDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Get restaurant ID from extras
        val restaurantId = RestaurantDetailFragmentArgs.fromBundle(requireArguments()).keyRestaurantId
        val viewModel by viewModels<RestaurantViewModel>()
        viewModel.getRestaurant(restaurantId)

        val composeReviewsView = binding.ratings
        composeReviewsView.setContent {
            MdcTheme {
                RestaurantScreen(viewModel)
            }
        }

        // Get reference to the restaurant
        ratingDialog = RatingDialogFragment()

    }

    private fun onBackArrowClicked() {
        requireActivity().onBackPressed()
    }

    private fun onAddRatingClicked() {
        ratingDialog?.show(childFragmentManager, RatingDialogFragment.TAG)
    }

    override fun onRating(rating: Rating) {
        // In a transaction, add the new rating and update the aggregate totals
        addRating(restaurantRef, rating)
                .addOnSuccessListener(requireActivity()) {
                    Log.d(TAG, "Rating added")

                    // Hide keyboard and scroll to top
                    hideKeyboard()
//                    binding.recyclerRatings.smoothScrollToPosition(0)
                }
                .addOnFailureListener(requireActivity()) { e ->
                    Log.w(TAG, "Add rating failed", e)

                    // Show failure message and hide keyboard
                    hideKeyboard()
                    Snackbar.make(
                        requireView().findViewById(android.R.id.content), "Failed to add rating",
                            Snackbar.LENGTH_SHORT).show()
                }
    }

    private fun addRating(restaurantRef: DocumentReference, rating: Rating): Task<Void> {
        // Create reference for new rating, for use inside the transaction
        val ratingRef = restaurantRef.collection("ratings").document()

        // In a transaction, add the new rating and update the aggregate totals
        return firestore.runTransaction { transaction ->
            val restaurant = transaction.get(restaurantRef).toObject<Restaurant>()
            if (restaurant == null) {
                throw Exception("Resraurant not found at ${restaurantRef.path}")
            }

            // Compute new number of ratings
            val newNumRatings = restaurant.numRatings + 1

            // Compute new average rating
            val oldRatingTotal = restaurant.avgRating * restaurant.numRatings
            val newAvgRating = (oldRatingTotal + rating.rating) / newNumRatings

            // Set new restaurant info
            restaurant.numRatings = newNumRatings
            restaurant.avgRating = newAvgRating

            // Commit to Firestore
            transaction.set(restaurantRef, restaurant)
            transaction.set(ratingRef, rating)

            null
        }
    }

    private fun hideKeyboard() {
        val view = requireActivity().currentFocus
        if (view != null) {
            (requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    companion object {

        private const val TAG = "RestaurantDetail"

        const val KEY_RESTAURANT_ID = "key_restaurant_id"
    }
}
