package com.chargecompass.app.ui

import java.text.DecimalFormat

private val grouping = DecimalFormat("#,##0")

/** 금액은 원 단위 정수로 표시한다. */
fun won(amount: Int): String = grouping.format(amount) + "원"
