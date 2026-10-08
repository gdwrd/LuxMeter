package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FilmStock
import com.example.sensor.ExposureCalculator
import com.example.sensor.HapticManager
import com.example.ui.theme.VintageBrassBright
import com.example.ui.theme.VintageBrassDark
import com.example.ui.theme.VintageBrassPrimary
import com.example.ui.theme.VintageIvoryMuted
import com.example.ui.theme.VintageIvoryText
import com.example.ui.theme.VintageLeatherDark
import com.example.ui.theme.VintageLeatherSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmStockPickerSheet(
  stocks: List<FilmStock>,
  selectedStock: FilmStock?,
  customIso: Int?,
  hapticManager: HapticManager,
  onStockSelected: (FilmStock) -> Unit,
  onCustomIsoSelected: (Int) -> Unit,
  onDismiss: () -> Unit
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val view = LocalView.current

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = VintageLeatherDark,
    contentColor = VintageIvoryText,
    dragHandle = {
      Box(
        modifier = Modifier
          .padding(vertical = 10.dp)
          .size(width = 44.dp, height = 4.dp)
          .background(VintageBrassDark, RoundedCornerShape(2.dp))
      )
    }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
      // Sheet Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "SELECT FILM STOCK",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            letterSpacing = 1.sp,
            color = VintageBrassBright
          )
          Text(
            text = "Visual film roll library with reciprocity profiles",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = VintageIvoryMuted
          )
        }

        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = VintageIvoryMuted)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Section 1: Film Roll Cartoon Grid
      Text(
        text = "POPULAR ANALOG EMULSIONS",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = VintageBrassPrimary,
        letterSpacing = 1.sp
      )

      Spacer(modifier = Modifier.height(8.dp))

      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(260.dp)
      ) {
        items(stocks) { stock ->
          val isSelected = selectedStock?.id == stock.id && customIso == null

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .background(
                if (isSelected) Color(0xFF2E2419) else VintageLeatherSurface
              )
              .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) VintageBrassBright else VintageBrassDark.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
              )
              .clickable {
                hapticManager.performDialClick(view)
                onStockSelected(stock)
                onDismiss()
              }
              .padding(8.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center
            ) {
              CartoonFilmRoll(
                stock = stock,
                size = 72.dp,
                isSelected = isSelected
              )

              Spacer(modifier = Modifier.height(4.dp))

              Text(
                text = stock.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Default,
                color = if (isSelected) VintageBrassBright else VintageIvoryText,
                maxLines = 1
              )

              Text(
                text = "${stock.type} • ISO ${stock.iso}",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = VintageIvoryMuted,
                maxLines = 1
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Section 2: Custom ISO row
      Text(
        text = "OR CHOOSE CUSTOM ISO SPEED",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = FontFamily.Monospace,
        color = VintageBrassPrimary,
        letterSpacing = 1.sp
      )

      Spacer(modifier = Modifier.height(8.dp))

      LazyRow(
        contentPadding = PaddingValues(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(ExposureCalculator.STANDARD_ISOS) { iso ->
          val isIsoSelected = customIso == iso

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(if (isIsoSelected) VintageBrassPrimary else VintageLeatherSurface)
              .border(1.dp, VintageBrassDark, RoundedCornerShape(8.dp))
              .clickable {
                hapticManager.performDialClick(view)
                onCustomIsoSelected(iso)
                onDismiss()
              }
              .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "$iso",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = if (isIsoSelected) Color.Black else VintageIvoryText
            )
          }
        }
      }
    }
  }
}
