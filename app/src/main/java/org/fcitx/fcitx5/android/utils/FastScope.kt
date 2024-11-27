package org.fcitx.fcitx5.android.utils

import org.mechdancer.dependency.Component
import org.mechdancer.dependency.Dependent
import org.mechdancer.dependency.ScopeEvent

open class FastScope {

    private val components = ArrayList<Component>()

    operator fun plusAssign(component: Component) {
        setup(component)
    }

    operator fun minusAssign(component: Component) {
        teardown(component)
    }

    open infix fun setup(component: Component) = synchronized(components) {
        components
            .add(component)
            .also {
                components.forEach {
                    if (it is Dependent)
                        it.handle(ScopeEvent.DependencyArrivedEvent(component))
                    if (component is Dependent)
                        component.handle(ScopeEvent.DependencyArrivedEvent(it))
                }
            }
    }

    open infix fun teardown(component: Component) = synchronized(components) {
        components
            .remove(component)
            .also {
                components.forEach {
                    if (it is Dependent)
                        it.handle(ScopeEvent.DependencyLeftEvent(component))
                }
            }
    }

    fun clear() = synchronized(components) {
        components.clear()
    }

}