package com.example.util

import java.text.DateFormat
import java.util.Date

/** Locale-aware "6 Nov 2026, 16:59"; used wherever a stored timestamp is shown. */
private val timestampFormat: DateFormat =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

fun formatTimestamp(millis: Long): String =
    if (millis <= 0L) "unknown" else timestampFormat.format(Date(millis))
