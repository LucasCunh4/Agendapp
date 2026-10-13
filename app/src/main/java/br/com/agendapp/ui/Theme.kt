package br.com.agendapp.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Gold=Color(0xFF987A3B)
val Green=Color(0xFF347B52)
private val Light=lightColorScheme(primary=Gold,onPrimary=Color.White,background=Color.White,surface=Color.White,
    surfaceContainer=Color(0xFFF6F6F6),surfaceVariant=Color(0xFFF6F6F6),onBackground=Color(0xFF343434),onSurface=Color(0xFF343434),secondary=Green)
private val Dark=darkColorScheme(primary=Color(0xFFCEAF70),background=Color(0xFF181818),surface=Color(0xFF181818),
    surfaceContainer=Color(0xFF262626),surfaceVariant=Color(0xFF262626),onSurface=Color(0xFFF0F0F0),onBackground=Color(0xFFF0F0F0),secondary=Color(0xFF83C79A))
@Composable fun AgendaTheme(dark: Boolean,content: @Composable ()->Unit) { MaterialTheme(colorScheme=if(dark) Dark else Light,content=content) }
