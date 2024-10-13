package com.dd3boh.outertune.ui.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.ui.menu.SelectionMediaMetadataMenu

@Composable
fun RowScope.SelectHeader(
    selectedItems: List<MediaMetadata>,
    totalItemCount: Int,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    menuState: MenuState,
    onDismiss: () -> Unit = {},
    onRemoveFromHistory: (() -> Unit)? = null
) {
    Text(
        text = "${selectedItems.size}/${totalItemCount} selected",
        modifier = Modifier.weight(1f)
    )

    // option menu
    IconButton(
        onClick = {
            menuState.show {
                SelectionMediaMetadataMenu(
                    selection = selectedItems,
                    onDismiss = menuState::dismiss,
                    clearAction = onDeselectAll,
                    onRemoveFromHistory = onRemoveFromHistory
                )
            }
        }
    ) {
        Icon(
            Icons.Rounded.MoreVert,
            contentDescription = null,
            tint = LocalContentColor.current
        )
    }

    // select/deselect all
    val allSelected = selectedItems.size < totalItemCount

    IconButton(
        onClick = if (allSelected) onSelectAll else onDeselectAll
    ) {
        Icon(
            imageVector = if (allSelected) Icons.Rounded.SelectAll else Icons.Rounded.Deselect,
            contentDescription = null,
            tint = LocalContentColor.current
        )
    }

    // close selection mode
    IconButton(
        onClick = onDismiss,
    ) {
        Icon(
            Icons.Rounded.Close,
            contentDescription = null
        )
    }
}