package com.mothblank.notasegura.navigation

sealed class AppScreen(val route: String) {
    object Timeline : AppScreen("timeline_screen")

    object AddEditItem : AppScreen("add_edit_item_screen?itemId={itemId}") {
        fun createRoute() = "add_edit_item_screen"
        fun editRoute(itemId: String) = "add_edit_item_screen?itemId=$itemId"
    }

    object Payments : AppScreen("payments_screen")

    object AddEditPayment : AppScreen("add_edit_payment_screen?paymentId={paymentId}") {
        fun createRoute() = "add_edit_payment_screen"
        fun editRoute(paymentId: String) = "add_edit_payment_screen?paymentId=$paymentId"
    }
}
