package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Palette: a warehouse floor. Concrete underneath, label stock on top, forklift yellow for
// the one thing you do all day (scan / add one), carbon-blue ink for everything you read.

val Ink = Color(0xFF1A2330)
val Concrete = Color(0xFFDADCD8)
val LabelStock = Color(0xFFF7F7F4)
val Forklift = Color(0xFFFFC20E)
val ForkliftSoft = Color(0xFFFFE58A)
val Steel = Color(0xFF55616D)
val Rule = Color(0xFFC4C8C3)
val Verified = Color(0xFF167A55)
val StopRed = Color(0xFFC23A22)

// Night shift
val NightFloor = Color(0xFF13171C)
val NightLabel = Color(0xFF1D232B)
val NightLabelRaised = Color(0xFF2A323C)
val NightText = Color(0xFFE8ECEF)
val NightSteel = Color(0xFFA3AEB9)
val NightRule = Color(0xFF343D47)
val NightForkliftDeep = Color(0xFF4A3C00)

// Kept under their old names so existing references (scanner banner, scan line) still compile.
val ScanGreen = Verified
val ScanRed = StopRed
