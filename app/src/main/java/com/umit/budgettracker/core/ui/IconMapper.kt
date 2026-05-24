package com.umit.budgettracker.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object IconMapper {
    fun getIcon(name: String): ImageVector {
        return when (name) {
            "shopping_cart" -> Icons.Default.ShoppingCart
            "receipt" -> Icons.Default.Receipt
            "home" -> Icons.Default.Home
            "directions_car" -> Icons.Default.DirectionsCar
            "directions_bus" -> Icons.Default.DirectionsBus
            "local_gas_station" -> Icons.Default.LocalGasStation
            "restaurant" -> Icons.Default.Restaurant
            "local_cafe" -> Icons.Default.LocalCafe
            "coffee" -> Icons.Default.Coffee
            "computer" -> Icons.Default.Computer
            "devices" -> Icons.Default.Devices
            "medical_services" -> Icons.Default.MedicalServices
            "health_and_safety" -> Icons.Default.HealthAndSafety
            "school" -> Icons.Default.School
            "movie" -> Icons.Default.Movie
            "checkroom" -> Icons.Default.Checkroom
            "home_work" -> Icons.Default.HomeWork
            "subscriptions" -> Icons.Default.Subscriptions
            "account_balance" -> Icons.Default.AccountBalance
            "payments" -> Icons.Default.Payments
            "credit_card" -> Icons.Default.CreditCard
            "beach_access" -> Icons.Default.BeachAccess
            "flight" -> Icons.Default.Flight
            "card_giftcard" -> Icons.Default.CardGiftcard
            "more_horiz" -> Icons.Default.MoreHoriz
            else -> Icons.Default.Category
        }
    }

    val allIconNames = listOf(
        "shopping_cart", "receipt", "home", "directions_car", "directions_bus",
        "local_gas_station", "restaurant", "local_cafe", "coffee", "computer", "devices",
        "medical_services", "health_and_safety", "school", "movie", "checkroom", "home_work",
        "subscriptions", "account_balance", "payments", "credit_card", "beach_access",
        "flight", "card_giftcard", "more_horiz", "category"
    )
}
