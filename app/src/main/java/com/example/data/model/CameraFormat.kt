package com.example.data.model

enum class CameraFormat(
  val displayName: String,
  val subtitle: String,
  val dimensionsMm: String,
  val aspectRatioLabel: String,
  val aspectRatioFloat: Float,
  val standardFramesPerRoll: Int,
  val description: String
) {
  FORMAT_35MM(
    displayName = "35mm",
    subtitle = "Full Frame 135",
    dimensionsMm = "24 × 36 mm",
    aspectRatioLabel = "3:2",
    aspectRatioFloat = 1.5f,
    standardFramesPerRoll = 36,
    description = "Standard miniature format, 36 or 24 exposures per roll."
  ),
  MEDIUM_645(
    displayName = "6×4.5",
    subtitle = "120 Medium Format",
    dimensionsMm = "56 × 41.5 mm",
    aspectRatioLabel = "4:3",
    aspectRatioFloat = 1.35f,
    standardFramesPerRoll = 16,
    description = "Ideal travel medium format, 16 exposures on standard 120 film."
  ),
  MEDIUM_66(
    displayName = "6×6",
    subtitle = "Square Format",
    dimensionsMm = "56 × 56 mm",
    aspectRatioLabel = "1:1",
    aspectRatioFloat = 1.0f,
    standardFramesPerRoll = 12,
    description = "Classic Hasselblad & TLR square format, 12 exposures per roll."
  ),
  MEDIUM_67(
    displayName = "6×7",
    subtitle = "Ideal Format",
    dimensionsMm = "56 × 67 mm",
    aspectRatioLabel = "5:4",
    aspectRatioFloat = 1.25f,
    standardFramesPerRoll = 10,
    description = "Legendary Pentax 67 & RB67 format, 10 giant negatives per roll."
  )
}
