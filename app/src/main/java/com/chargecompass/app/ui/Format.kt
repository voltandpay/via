package com.chargecompass.app.ui

import java.text.DecimalFormat

private val grouping = DecimalFormat("#,##0")

/** 금액은 원 단위 정수로 표시한다. */
fun won(amount: Int): String = grouping.format(amount) + "원"

private const val WORD_JOINER = '⁠'

/**
 * 한글은 글자 사이 어디서나 줄이 바뀌어 "3시간 남 / 음"처럼 한 글자만 넘어간다.
 * 띄어쓰기가 아닌 글자 사이에 단어 결합자(폭 0)를 넣어 띄어쓰기에서만 줄이 바뀌게 한다.
 */
fun keepWords(text: String): String {
    if (text.length < 2) return text
    val sb = StringBuilder(text.length * 2)
    for (i in text.indices) {
        val ch = text[i]
        if (i > 0 && !ch.isWhitespace() && !text[i - 1].isWhitespace()) sb.append(WORD_JOINER)
        sb.append(ch)
    }
    return sb.toString()
}

