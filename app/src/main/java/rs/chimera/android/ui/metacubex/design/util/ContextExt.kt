package rs.chimera.android.ui.metacubex.design.util

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup

val Context.layoutInflater: LayoutInflater
    get() = LayoutInflater.from(this)

val Context.root: ViewGroup?
    get() = (this as? android.app.Activity)?.findViewById(android.R.id.content)
