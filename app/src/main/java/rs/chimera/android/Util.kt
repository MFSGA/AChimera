package rs.chimera.android

import android.content.Intent
import kotlin.reflect.KClass

val KClass<*>.intent: Intent
    get() = Intent(Global.application, this.java)
