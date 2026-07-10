package com.umit.budgettracker.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
            "account_balance_wallet" -> Icons.Default.AccountBalanceWallet
            "savings" -> Icons.Default.Savings
            "attach_money" -> Icons.Default.AttachMoney
            "currency_exchange" -> Icons.Default.CurrencyExchange
            "payments" -> Icons.Default.Payments
            "receipt_long" -> Icons.AutoMirrored.Filled.ReceiptLong
            "credit_card" -> Icons.Default.CreditCard
            "shopping_bag" -> Icons.Default.ShoppingBag
            "shopping_basket" -> Icons.Default.ShoppingBasket
            "shopping_cart_checkout" -> Icons.Default.ShoppingCartCheckout
            "store" -> Icons.Default.Store
            "local_mall" -> Icons.Default.LocalMall
            "local_grocery_store" -> Icons.Default.LocalGroceryStore
            "fastfood" -> Icons.Default.Fastfood
            "local_dining" -> Icons.Default.LocalDining
            "bakery_dining" -> Icons.Default.BakeryDining
            "breakfast_dining" -> Icons.Default.BreakfastDining
            "lunch_dining" -> Icons.Default.LunchDining
            "dinner_dining" -> Icons.Default.DinnerDining
            "restaurant_menu" -> Icons.Default.RestaurantMenu
            "emoji_food_beverage" -> Icons.Default.EmojiFoodBeverage
            "icecream" -> Icons.Default.Icecream
            "local_bar" -> Icons.Default.LocalBar
            "wine_bar" -> Icons.Default.WineBar
            "liquor" -> Icons.Default.Liquor
            "smoking_rooms" -> Icons.Default.SmokingRooms
            "smoke_free" -> Icons.Default.SmokeFree
            "directions_bike" -> Icons.AutoMirrored.Filled.DirectionsBike
            "directions_walk" -> Icons.AutoMirrored.Filled.DirectionsWalk
            "train" -> Icons.Default.Train
            "local_taxi" -> Icons.Default.LocalTaxi
            "two_wheeler" -> Icons.Default.TwoWheeler
            "beach_access" -> Icons.Default.BeachAccess
            "flight" -> Icons.Default.Flight
            "flight_takeoff" -> Icons.Default.FlightTakeoff
            "apartment" -> Icons.Default.Apartment
            "chair" -> Icons.Default.Chair
            "bed" -> Icons.Default.Bed
            "bathtub" -> Icons.Default.Bathtub
            "shower" -> Icons.Default.Shower
            "weekend" -> Icons.Default.Weekend
            "yard" -> Icons.Default.Yard
            "cleaning_services" -> Icons.Default.CleaningServices
            "dry_cleaning" -> Icons.Default.DryCleaning
            "construction" -> Icons.Default.Construction
            "content_cut" -> Icons.Default.ContentCut
            "pets" -> Icons.Default.Pets
            "baby_changing_station" -> Icons.Default.BabyChangingStation
            "sports_soccer" -> Icons.Default.SportsSoccer
            "sports_esports" -> Icons.Default.SportsEsports
            "music_note" -> Icons.Default.MusicNote
            "book" -> Icons.Default.Book
            "menu_book" -> Icons.AutoMirrored.Filled.MenuBook
            "palette" -> Icons.Default.Palette
            "camera_alt" -> Icons.Default.CameraAlt
            "photo" -> Icons.Default.Photo
            "park" -> Icons.Default.Park
            "fitness_center" -> Icons.Default.FitnessCenter
            "spa" -> Icons.Default.Spa
            "local_pharmacy" -> Icons.Default.LocalPharmacy
            "medication" -> Icons.Default.Medication
            "child_care" -> Icons.Default.ChildCare
            "work" -> Icons.Default.Work
            "business_center" -> Icons.Default.BusinessCenter
            "wifi" -> Icons.Default.Wifi
            "water_drop" -> Icons.Default.WaterDrop
            "bolt" -> Icons.Default.Bolt
            "security" -> Icons.Default.Security
            "redeem" -> Icons.Default.Redeem
            "celebration" -> Icons.Default.Celebration
            "cake" -> Icons.Default.Cake
            "volunteer_activism" -> Icons.Default.VolunteerActivism
            "eco" -> Icons.Default.Eco
            "recycling" -> Icons.Default.Recycling
            "sell" -> Icons.Default.Sell
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
        "flight", "card_giftcard", "more_horiz", "category",
        "account_balance_wallet", "savings", "attach_money", "currency_exchange", "receipt_long",
        "shopping_bag", "shopping_basket", "store", "local_mall", "fastfood", "local_dining",
        "bakery_dining", "directions_bike", "directions_walk", "train", "local_taxi", "two_wheeler",
        "flight_takeoff", "apartment", "chair", "cleaning_services", "construction", "pets",
        "sports_soccer", "sports_esports", "music_note", "book", "menu_book", "palette",
        "camera_alt", "photo", "park", "fitness_center", "spa", "local_pharmacy", "medication",
        "child_care", "work", "business_center", "wifi", "water_drop", "bolt", "security",
        "redeem", "celebration", "cake", "volunteer_activism", "eco", "recycling", "sell",
        "shopping_cart_checkout", "local_grocery_store", "breakfast_dining", "lunch_dining",
        "dinner_dining", "restaurant_menu", "emoji_food_beverage", "icecream", "local_bar",
        "wine_bar", "liquor", "smoking_rooms", "smoke_free", "bed", "bathtub", "shower",
        "weekend", "yard", "dry_cleaning", "content_cut", "baby_changing_station"
    )
}
