package com.offlinevault.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.graphics.vector.ImageVector

/** Maps a stored icon key to a Material icon. New keys fall back to a lock. */
object VaultIcons {

    val options: List<String> = listOf("lock", "person", "work", "bank", "shopping", "cloud", "shield", "favorite")

    fun forKey(key: String): ImageVector = when (key) {
        "person" -> Icons.Filled.Person
        "work" -> Icons.Filled.Work
        "bank" -> Icons.Filled.AccountBalance
        "shopping" -> Icons.Filled.ShoppingCart
        "cloud" -> Icons.Filled.Cloud
        "shield" -> Icons.Filled.Shield
        "favorite" -> Icons.Filled.Favorite
        else -> Icons.Filled.Lock
    }
}
