package com.dd3boh.outertune.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.offline.Download
import com.dd3boh.outertune.R
import com.dd3boh.outertune.utils.makeTimeString

val GridMenuItemHeight = 96.dp

@Composable
fun GridMenu(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier,
        contentPadding = contentPadding,
        content = content
    )
}

fun LazyGridScope.GridMenuItem(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    @StringRes title: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = GridMenuItem(
    modifier = modifier,
    icon = {
        Icon(
            painter = painterResource(icon),
            contentDescription = null
        )
    },
    title = title,
    enabled = enabled,
    onClick = onClick
)

fun LazyGridScope.GridMenuItem(
    modifier: Modifier = Modifier,
    icon: @Composable BoxScope.() -> Unit,
    @StringRes title: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    item {
        Column(
            modifier = modifier
                .clip(ShapeDefaults.Large)
                .height(GridMenuItemHeight)
                .clickable(
                    enabled = enabled,
                    onClick = onClick
                )
                .alpha(if (enabled) 1f else 0.5f)
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
                content = icon
            )
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

fun LazyGridScope.GridMenuItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    tint: @Composable () -> Color = { LocalContentColor.current },
    @StringRes title: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) = GridMenuItem(
    modifier = modifier,
    icon = {
        Icon(
            imageVector = icon,
            tint = tint(),
            contentDescription = null
        )
    },
    title = title,
    enabled = enabled,
    onClick = onClick
)



fun LazyGridScope.DownloadGridMenu(
    @Download.State state: Int?,
    onRemoveDownload: () -> Unit,
    onDownload: () -> Unit,
) {
    when (state) {
        Download.STATE_COMPLETED -> {
            GridMenuItem(
                icon = Icons.Rounded.OfflinePin,
                title = R.string.remove_download,
                onClick = onRemoveDownload
            )
        }

        Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> {
            GridMenuItem(
                icon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                },
                title = R.string.downloading,
                onClick = onRemoveDownload
            )
        }

        else -> {
            GridMenuItem(
                icon = Icons.Rounded.Download,
                title = R.string.download,
                onClick = onDownload
            )
        }
    }
}

fun LazyGridScope.SleepTimerGridMenu(
    modifier: Modifier = Modifier,
    sleepTimerTimeLeft: Long,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    item {
        Column(
            modifier = modifier
                .clip(ShapeDefaults.Large)
                .height(GridMenuItemHeight)
                .clickable(
                    onClick = onClick
                )
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
                content = {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        modifier = Modifier.alpha(if (enabled) 1f else 0.5f)
                    )
                }
            )
            Text(
                text = if (enabled) makeTimeString(sleepTimerTimeLeft) else stringResource(
                    id = R.string.sleep_timer
                ),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
