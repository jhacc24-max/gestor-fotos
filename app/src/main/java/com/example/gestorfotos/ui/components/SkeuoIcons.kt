package com.example.gestorfotos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Estilos skeuomórficos: cada uno es una combinación de metal/material real que
 * da contexto semántico (cromo = acción neutra, latón = favoritos/buscar,
 * cuero = álbumes, vidrio rojo = destructivo, vidrio verde = confirmar).
 */
enum class SkeuoStyle { CHROME, BRASS, LEATHER, RUBY, EMERALD }

private data class SkeuoPalette(val top: Color, val bottom: Color, val rim: Color, val glyph: Color)

private fun paletteFor(style: SkeuoStyle): SkeuoPalette = when (style) {
    SkeuoStyle.CHROME -> SkeuoPalette(Color(0xFF54545A), Color(0xFF232326), Color(0xFF6E6E74), Color(0xFFF1F1EF))
    SkeuoStyle.BRASS -> SkeuoPalette(Color(0xFFF3C463), Color(0xFF9C6A1E), Color(0xFFFFE3A3), Color(0xFF3A2405))
    SkeuoStyle.LEATHER -> SkeuoPalette(Color(0xFF8A5A3B), Color(0xFF4A2E1C), Color(0xFFAD7A54), Color(0xFFF3E7D8))
    SkeuoStyle.RUBY -> SkeuoPalette(Color(0xFFE0697D), Color(0xFF8C2739), Color(0xFFF29AA8), Color(0xFFFFF3F3))
    SkeuoStyle.EMERALD -> SkeuoPalette(Color(0xFF63D2B8), Color(0xFF17836A), Color(0xFF9CE9D6), Color(0xFF06251D))
}

/** Botón redondo tipo "gel" (icono clicable, con relieve, brillo especular y sombra). */
@Composable
fun SkeuoIconButton(
    icon: ImageVector,
    contentDescription: String?,
    style: SkeuoStyle = SkeuoStyle.CHROME,
    size: Dp = 40.dp,
    onClick: () -> Unit
) {
    val p = paletteFor(style)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .shadow(elevation = 5.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(p.top, p.bottom)))
            .border(0.8.dp, p.rim, CircleShape)
            .clickable(interactionSource = interaction, indication = rememberRipple(bounded = false), onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SpecularHighlight(size)
        Icon(icon, contentDescription = contentDescription, tint = p.glyph, modifier = Modifier.size(size * 0.5f))
    }
}

/** Versión no clicable, para indicadores/decoración (favorito en una tarjeta, íconos de pestaña). */
@Composable
fun SkeuoIcon(
    icon: ImageVector,
    contentDescription: String?,
    style: SkeuoStyle = SkeuoStyle.CHROME,
    size: Dp = 30.dp,
    selected: Boolean = true
) {
    val p = paletteFor(style)
    val alpha = if (selected) 1f else 0.55f
    Box(
        modifier = Modifier
            .size(size)
            .shadow(elevation = if (selected) 4.dp else 1.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(p.top.copy(alpha = alpha), p.bottom.copy(alpha = alpha))))
            .border(0.6.dp, p.rim.copy(alpha = alpha), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        SpecularHighlight(size)
        Icon(icon, contentDescription = contentDescription, tint = p.glyph.copy(alpha = alpha), modifier = Modifier.size(size * 0.52f))
    }
}

/** Placa skeuomórfica cuadrada (para íconos grandes tipo álbum/carpeta, look de cuero repujado). */
@Composable
fun SkeuoPlate(
    icon: ImageVector,
    contentDescription: String?,
    style: SkeuoStyle = SkeuoStyle.LEATHER,
    size: Dp = 44.dp
) {
    val p = paletteFor(style)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(p.top, p.bottom)))
            .border(0.8.dp, p.rim, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        SpecularHighlight(size, corner = true)
        Icon(icon, contentDescription = contentDescription, tint = p.glyph, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun BoxScope.SpecularHighlight(size: Dp, corner: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.66f)
            .fillMaxHeight(0.34f)
            .align(Alignment.TopCenter)
            .padding(top = size * 0.09f)
            .clip(if (corner) RoundedCornerShape(50) else RoundedCornerShape(50))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0f))
                )
            )
    )
}
