package com.vanfieldguide

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Forest = Color(0xFF183F32)
private val Cream = Color(0xFFF8F5ED)
private val Copper = Color(0xFF98532F)

@Composable fun VanGuardTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Forest, onPrimary = Color.White,
            primaryContainer = Color(0xFFDCE9DD), onPrimaryContainer = Color(0xFF123325),
            secondary = Copper, onSecondary = Color.White,
            secondaryContainer = Color(0xFFF6E3D3), onSecondaryContainer = Color(0xFF542D19),
            tertiary = Color(0xFF506443), onTertiary = Color.White,
            background = Cream, onBackground = Color(0xFF202D27),
            surface = Cream, onSurface = Color(0xFF202D27),
            surfaceVariant = Color(0xFFECEDE3), onSurfaceVariant = Color(0xFF505C52),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0EEE5),
            surfaceContainer = Color(0xFFECEBE1), surfaceContainerHigh = Color(0xFFE4E6DA),
            surfaceContainerHighest = Color(0xFFE0E5D9),
            outline = Color(0xFF758274), outlineVariant = Color(0xFFD3D8CB)),
        typography = base.copy(
            headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
            headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)),
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content)
}

@Composable fun VanGuardHeader(tab: Int, clear: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Forest).padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.van_guard_art), contentDescription = null,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)))
            Column(Modifier.weight(1f)) {
                Text("VAN GUARD", color = Cream, fontSize = 28.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                Text("Your vanlife field guide", color = Color(0xFFD3DFD3), fontSize = 15.sp, lineHeight = 20.sp)
            }
        }
        Text(when (tab) { 1 -> "The knowledge you carry."; 2 -> "Every adventure, in view."; 3 -> "Ready for the road."; 4 -> "From photo to manual."; else -> "Your van, on hand." },
            style = MaterialTheme.typography.headlineMedium, color = Cream)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(modifier = Modifier.weight(1f, fill = false), color = Color(0xFF2B5142), contentColor = Color(0xFFE7EEDC), shape = RoundedCornerShape(50)) {
                Text("ON-DEVICE AI  ·  ${GemmaRuntime.lastBackend}", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
            }
            clear()
        }
    }
}

@Composable fun VanGuardNavigation(tab: Int, select: (Int) -> Unit) {
    val destinations = listOf(Triple(0, "Find", R.drawable.ic_find), Triple(1, "Guides", R.drawable.ic_guides),
        Triple(2, "Photos", R.drawable.ic_photos), Triple(4, "Scan", R.drawable.ic_scan), Triple(3, "Setup", R.drawable.ic_setup))
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
        destinations.forEach { (index, label, icon) ->
            NavigationBarItem(selected = tab == index, onClick = { select(index) },
                icon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(23.dp)) },
                label = { Text(label) }, colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer))
        }
    }
}
