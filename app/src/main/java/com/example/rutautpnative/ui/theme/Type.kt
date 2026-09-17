package com.example.rutautpnative.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

//----Familias tipográficas----
val HankenGrotesk = FontFamily.Default   // → reemplazar con FontFamily(Font(R.font.hanken_grotesk_...))
val BeVietnam     = FontFamily.Default   // → reemplazar con FontFamily(Font(R.font.be_vietnam_pro_...))
val JetBrainsMono = FontFamily.Monospace // → reemplazar con FontFamily(Font(R.font.jetbrains_mono_...))

//----Tokens tipograficos----
val DisplayLg       = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, letterSpacing = (-0.6).sp)
val DisplayLgPhone  = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.6).sp)
val HeadlineLg      = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp)
val HeadlineMd      = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,      fontSize = 24.sp)
val HeadlineSm      = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,      fontSize = 20.sp)
val HeadlineXs      = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,      fontSize = 18.sp)
val HeadlineBody    = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,      fontSize = 16.sp)

// Display numericos
val DisplayNumberLg = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp)
val DisplayNumberMd = TextStyle(fontFamily = HankenGrotesk, fontWeight = FontWeight.Bold,      fontSize = 24.sp)

// Body — Be Vietnam Pro
val BodyLg          = TextStyle(fontFamily = BeVietnam, fontWeight = FontWeight.Normal,  fontSize = 18.sp, lineHeight = 26.sp)
val BodyMd          = TextStyle(fontFamily = BeVietnam, fontWeight = FontWeight.Normal,  fontSize = 16.sp, lineHeight = 24.sp)
val BodySm          = TextStyle(fontFamily = BeVietnam, fontWeight = FontWeight.Normal,  fontSize = 14.sp, lineHeight = 20.sp)
val BodyXs          = TextStyle(fontFamily = BeVietnam, fontWeight = FontWeight.Normal,  fontSize = 13.sp)
val BodyMdMedium    = TextStyle(fontFamily = BeVietnam, fontWeight = FontWeight.Medium,  fontSize = 16.sp, lineHeight = 24.sp)
val BodySmMedium    = TextStyle(fontFamily = BeVietnam, fontWeight = FontWeight.Medium,  fontSize = 15.sp)
val BodyXsMedium    = TextStyle(fontFamily = BeVietnam, fontWeight = FontWeight.Medium,  fontSize = 13.sp)

// Labels UPPERCASE -- JetBrains Mono
val LabelCapsLg     = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 1.5.sp)
val LabelCapsMd     = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.8.sp)
val LabelCapsSm     = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 2.4.sp)