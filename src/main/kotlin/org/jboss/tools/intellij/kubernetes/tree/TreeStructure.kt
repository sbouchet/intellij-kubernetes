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

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.ide.util.treeView.PresentableNodeDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IconLoader
import io.fabric8.kubernetes.api.model.HasMetadata
import org.jboss.tools.intellij.kubernetes.model.IResourceModel
import org.jboss.tools.intellij.kubernetes.model.context.IContext
import java.util.Optional
import javax.swing.Icon

/**
 * A factory that creates nodes (PresentableNodeDescriptor) for a (tree-) model.
 *
 * @see PresentableNodeDescriptor
 */
open class TreeStructure(
        private val model: IResourceModel,
        private val extensionPoint: ExtensionPointName<ITreeStructureContributionFactory> =
                ExtensionPointName.create("org.jboss.tools.intellij.kubernetes.structureContribution"))
    : AbstractTreeStructure() {

    private val contributions by lazy {
        listOf(
                *getTreeStructureDefaults(model).toTypedArray(),
                *getTreeStructureExtensions(model).toTypedArray()
        )
    }

    override fun getRootElement(): Any {
        return model
    }

    override fun getChildElements(element: Any): Array<Any> {
        return when (element) {
            rootElement -> model.getAllContexts().toTypedArray()
            else -> getValidContributions()
                    .flatMap { getChildElements(element, it) }
                    .toTypedArray()
        }
    }

    private fun getChildElements(element: Any, contribution: ITreeStructureContribution): Collection<Any> {
        return try {
            contribution.getChildElements(element)
        } catch (e: java.lang.Exception) {
            logger<TreeStructure>().warn(e)
            listOf(e)
        }
    }

    override fun getParentElement(element: Any): Any? {
        val parent: Optional<Any?> = getValidContributions().stream()
                .map { getParentElement(element, it) }
                .filter { it != null }
                .findAny()
        return parent.orElse(rootElement)
    }

    private fun getParentElement(element: Any, contribution: ITreeStructureContribution): Any? {
        return try {
            contribution.getParentElement(element)
        } catch (e: java.lang.Exception) {
            logger<TreeStructure>().warn("Could not get parent for element $element", e)
            null
        }
    }

    override fun isAlwaysLeaf(element: Any): Boolean {
        return false
    }

    override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?): NodeDescriptor<*> {
        val descriptor: NodeDescriptor<*>? =
                getValidContributions()
                        .map { it.createDescriptor(element, parent) }
                        .find { it != null }
        if (descriptor != null) {
            return descriptor
        }
        return when (element) {
            is IContext -> ContextDescriptor(element, parent, model = model)
            is Exception -> ErrorDescriptor(element, parent, model = model)
            is Folder -> FolderDescriptor(element, parent, model = model)
            else -> Descriptor(element, parent, model = model)
        }
    }

    private fun getValidContributions(): Collection<ITreeStructureContribution> {
        return contributions
                .filter { it.canContribute() }
    }

    private fun getTreeStructureExtensions(model: IResourceModel): List<ITreeStructureContribution> {
        return extensionPoint.extensionList
                .map { it.create(model) }
    }

    protected open fun getTreeStructureDefaults(model: IResourceModel): List<ITreeStructureContribution> {
        return listOf(
                OpenShiftStructure(model),
                KubernetesStructure(model))
    }

    override fun commit() = Unit

    override fun hasSomethingToCommit() = false

    override fun isToBuildChildrenInBackground(element: Any) = true

    open class ContextDescriptor<C : IContext>(
            context: C,
            parent: NodeDescriptor<*>? = null,
            model: IResourceModel)
        : Descriptor<C>(
            context,
            parent,
            model
    ) {
        override fun getLabel(element: C): String {
            return if (element.context.context == null) {
                "<unknown context>"
            } else {
                element.context.name
            }
        }

        override fun getIcon(element: C): Icon? {
            return IconLoader.getIcon("/icons/kubernetes-cluster.svg")
        }
    }

    open class ResourcePropertyDescriptor<T>(
            element: T,
            parent: NodeDescriptor<*>?,
            model: IResourceModel
    ) : Descriptor<T>(
            element,
            parent,
            model
    ) {
        override fun getElement(): T? {
            return null
        }
    }

    private class FolderDescriptor(category: Folder, parent: NodeDescriptor<*>?, model: IResourceModel)
        : Descriptor<Folder>(
            category, parent,
            model = model
    ) {
        override fun isMatching(element: Any?): Boolean {
            // change in resource cathegory is notified as change of resource kind
            return this.element?.kind == element
        }

        override fun invalidate() {
            model.invalidate(element?.kind)
        }

        override fun getLabel(element: Folder): String {
            return element.label
        }
    }

    private class ErrorDescriptor(exception: java.lang.Exception, parent: NodeDescriptor<*>?, model: IResourceModel)
        : Descriptor<java.lang.Exception>(
            exception,
            parent,
            model
    ) {
        override fun getLabel(element: java.lang.Exception): String {
            return "Error: " + element.message
        }

        override fun getIcon(element: java.lang.Exception): Icon? {
            return AllIcons.General.BalloonError
        }
    }

    open class ResourceDescriptor<T : HasMetadata>(
            element: T,
            parent: NodeDescriptor<*>?,
            model: IResourceModel) : Descriptor<T>(element, parent, model) {

        override fun getLabel(element: T): String {
            return element.metadata.name
        }
    }

    open class Descriptor<T>(
            element: T,
            parent: NodeDescriptor<*>?,
            protected val model: IResourceModel
    ) : PresentableNodeDescriptor<T>(null, parent) {

        private val element = element

        override fun update(presentation: PresentationData) {
            presentation.presentableText = getLabel(element)
            updateIcon(getIcon(element), presentation)
        }

        open fun isMatching(element: Any?): Boolean {
            return this.element == element
        }

        override fun getElement(): T? {
            return element;
        }

        open fun invalidate() {
            model.invalidate(element)
        }

        protected open fun getLabel(element: T): String {
            return element.toString()
        }

        protected open fun getIcon(element: T): Icon? {
            return null
        }

        private fun updateIcon(icon: Icon?, presentation: PresentationData) {
            if (icon != null) {
                presentation.setIcon(icon)
            }
        }
    }

    data class Folder(val label: String, val kind: Class<out HasMetadata>?)

    abstract class DescriptorFactory<R : HasMetadata>(protected val resource: R) {
        abstract fun create(parent: NodeDescriptor<*>?, model: IResourceModel): NodeDescriptor<R>?
    }
}

