package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
  displayLarge = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 56.sp,
    lineHeight = 64.sp,
    letterSpacing = (-2).sp,
    fontFeatureSettings = "tnum"
  ),
  displayMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 44.sp,
    lineHeight = 52.sp,
    letterSpacing = (-1).sp,
    fontFeatureSettings = "tnum"
  ),
  headlineMedium = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 26.sp,
    lineHeight = 34.sp
  )
)
