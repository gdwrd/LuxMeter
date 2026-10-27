package com.example

import com.example.data.model.FilmStock
import com.example.ui.components.filmLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class CartoonFilmRollTest {
  @Test fun cinestillArtworkDoesNotLabelBlackAndWhiteOrUnknownFilmsAs800T() {
    val stock = FilmStock(name = "BwXX", brand = "CineStill", iso = 250, type = "Black & White",
      reciprocityFactor = 1.0, description = "Test", badgeStyle = "BWXX", hasReciprocityProfile = false)
    assertEquals("BwXX", filmLabel(stock).family)
    assertEquals("800T", filmLabel(stock.copy(name = "CineStill 800T", type = "Tungsten Cine")).family)
    assertEquals("50D", filmLabel(stock.copy(name = "50D", type = "Color Negative")).family)
    assertEquals("400D", filmLabel(stock.copy(name = "400D", type = "Color Negative")).family)
    assertEquals("FILM", filmLabel(stock.copy(name = "My tests", isCustom = true)).family)
  }
}
