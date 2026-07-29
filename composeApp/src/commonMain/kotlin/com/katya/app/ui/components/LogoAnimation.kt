package com.katya.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import katya.composeapp.generated.resources.Res
import katya.composeapp.generated.resources.logo_pomogator
import org.jetbrains.compose.resources.painterResource

@Composable
fun LogoAnimation(
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    Image(
        painter = painterResource(Res.drawable.logo_pomogator),
        contentDescription = "Logo",
        modifier = modifier.size(size),
    )
}
