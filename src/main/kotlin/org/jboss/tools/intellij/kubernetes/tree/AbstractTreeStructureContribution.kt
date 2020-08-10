/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.intellij.kubernetes.tree

import org.jboss.tools.intellij.kubernetes.model.IResourceModel

abstract class AbstractTreeStructureContribution(override val model: IResourceModel): ITreeStructureContribution {

    protected fun getRootElement(): Any? {
        return model.getCurrentContext()
    }

    fun <T> element(initializer: ElementNode<T>.() -> Unit): ElementNode<T> {
        return ElementNode<T>().apply(initializer)
    }

    class ElementNode<T> {
        private var parentElementsProvider: ((element: T) -> Any?)? = null
        private var childElementsProvider: ((element: T) -> Collection<Any>)? = null
        private lateinit var anchorProvider: (element: Any) -> Boolean

        fun anchor(provider: (element: Any) -> Boolean): ElementNode<T> {
            this.anchorProvider = provider
            return this
        }

        fun parentElements(provider: ((element: T) -> Any?)?): ElementNode<T> {
            this.parentElementsProvider = provider
            return this
        }

        fun childElements(provider: (element: T) -> Collection<Any>): ElementNode<T> {
            this.childElementsProvider = provider
            return this
        }

        fun isAnchor(element: Any): Boolean {
            return anchorProvider.invoke(element)
        }

        fun getChildElements(element: Any): Collection<Any> {
            val typedElement = element as? T ?: return emptyList()
            return childElementsProvider?.invoke(typedElement) ?: return emptyList()
        }

        fun getParentElements(element: Any): Any? {
            val typedElement = element as? T ?: return null
            return parentElementsProvider?.invoke(typedElement) ?: return null
        }

    }
}