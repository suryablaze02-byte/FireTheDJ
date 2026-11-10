package com.autodj.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The FOSS build has no casting: these stand in for the full build's CastUi, and draw nothing.

@Composable
fun CastButton(casting: Boolean, diameter: Dp = 40.dp, onClick: () -> Unit) = Unit

@Composable
fun rememberCasting(): Boolean = false

@Composable
fun CastDialog(onClose: () -> Unit) = Unit

@Composable
fun CastCapturePrompts(artworkFor: (String) -> String?) = Unit
