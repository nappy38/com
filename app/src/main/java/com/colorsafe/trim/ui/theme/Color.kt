package com.colorsafe.trim.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * 水彩画から起こしたパレット。
 * 紙の白・花びらのバラ色・葉のセージの3系統で組む。
 *
 * 純白と純黒は使わない。水彩は紙の温かみと墨の柔らかさでできているので、
 * そこを白黒にすると一気に事務的に見える。
 */

val AppPaper = Color(0xFFFAF6F3)      // 紙。ほんのり温かい白
val AppPaperCard = Color(0xFFFFFDFC)  // カード面。紙よりわずかに明るい
val AppRose = Color(0xFFC2607B)       // 主色。花の中心の濃い赤紫
val AppRoseLight = Color(0xFFEBB9BC)  // 淡い花びら
val AppBlush = Color(0xFFF4E1DE)      // 面に敷く淡いピンク
val AppSage = Color(0xFF7C9A94)       // 葉の緑
val AppSageLight = Color(0xFFDCE5E2)
val AppInk = Color(0xFF453A3C)        // 文字。黒ではなく墨寄り
val AppInkSoft = Color(0xFF8D7B7C)    // 補足の文字
val AppLine = Color(0xFFE8DAD6)       // 罫線
val AppDanger = Color(0xFFB2465A)     // エラー。原色の赤は水彩から浮く

/** 背景に落とすにじみ。薄く重ねて紙のムラに見せる */
val AppBlushWash = Color(0x3CE7A79E)
val AppSageWash = Color(0x2E7F9A94)
val AppPeachWash = Color(0x30EFC2A4)

// 旧テーマ(白ベース)の名残。他から参照されていれば残す必要がある。
val AppWhite = Color(0xFFFFFFFF)
