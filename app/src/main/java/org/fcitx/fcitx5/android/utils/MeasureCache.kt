/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.utils

class MeasureCache {

    private var widthSpec: Int? = null

    private var heightSpec: Int? = null

    private var requestedId = 0

    private var measuredId = 0

    var width = 0

    var height = 0

    fun setMeasureSpecs(widthMeasureSpec: Int, heightMeasureSpec: Int): Boolean {
        if (measuredId == requestedId && widthMeasureSpec == widthSpec && heightMeasureSpec == heightSpec) {
            return false
        }
        widthSpec = widthMeasureSpec
        heightSpec = heightMeasureSpec
        measuredId = requestedId
        return true
    }

    fun clear() {
        widthSpec = null
        heightSpec = null
        requestedId++
    }

}