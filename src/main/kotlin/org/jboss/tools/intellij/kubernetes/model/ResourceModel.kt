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
package org.jboss.tools.intellij.kubernetes.model

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.NamedContext
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import org.jboss.tools.intellij.kubernetes.model.context.ContextFactory
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext
import org.jboss.tools.intellij.kubernetes.model.context.IContext
import org.jboss.tools.intellij.kubernetes.model.context.IActiveContext.*
import org.jboss.tools.intellij.kubernetes.model.util.KubeConfigContexts
import java.util.function.Predicate

interface IResourceModel {

    fun getClient(): KubernetesClient?
    fun setCurrentContext(context: IContext)
    fun getCurrentContext(): IActiveContext<out HasMetadata, out KubernetesClient>?
    fun getAllContexts(): List<IContext>
    fun setCurrentNamespace(namespace: String)
    fun getCurrentNamespace(): String?
    fun <R: HasMetadata> resources(kind: Class<R>): Namespaceable<R>
    fun getKind(resource: HasMetadata): Class<out HasMetadata>
    fun invalidate(element: Any?)
    fun addListener(listener: ModelChangeObservable.IResourceChangeListener)
}

class ResourceModel(
    private val observable: IModelChangeObservable = ModelChangeObservable(),
    contextFactory: (IModelChangeObservable, NamedContext) -> IActiveContext<out HasMetadata, out KubernetesClient> =
        ContextFactory()::create,
    config: KubeConfigContexts = KubeConfigContexts()
) : IResourceModel {

    private val contexts: IContexts = Contexts(observable, contextFactory, config)

    override fun setCurrentContext(context: IContext) {
        contexts.setCurrent(context)
    }

    override fun getCurrentContext(): IActiveContext<out HasMetadata, out KubernetesClient>? {
        return contexts.current
    }

    override fun getAllContexts(): List<IContext> {
        return contexts.allContexts
    }

    override fun getClient(): KubernetesClient? {
        return contexts.current?.client
    }

    override fun addListener(listener: ModelChangeObservable.IResourceChangeListener) {
        observable.addListener(listener)
    }

    override fun setCurrentNamespace(namespace: String) {
        contexts.current?.setCurrentNamespace(namespace)
    }

    override fun getCurrentNamespace(): String? {
        try {
            return contexts.current?.getCurrentNamespace() ?: return null
        } catch (e: KubernetesClientException) {
            throw ResourceException(
                "Could not get current namespace for server ${contexts.current?.client?.masterUrl}", e)
        }
    }

    override fun <R: HasMetadata> resources(kind: Class<R>): Namespaceable<R> {
        return Namespaceable(kind, this)
    }

    fun <R: HasMetadata> getResources(kind: Class<R>, namespaced: ResourcesIn, filter: Predicate<R>? = null): Collection<R> {
        try {
            val resources = contexts.current?.getResources(kind, namespaced) ?: return emptyList()
            return if (filter == null) {
                resources
            } else {
                resources.filter { filter.test(it) }
            }
        } catch (e: KubernetesClientException) {
            if (isNotFound(e)) {
                return emptyList()
            }
            throw ResourceException("Could not get ${kind.simpleName}s for server ${contexts.current?.client?.masterUrl}", e)
        }
    }

    override fun getKind(resource: HasMetadata): Class<out HasMetadata> {
        return resource::class.java
    }

    override fun invalidate(element: Any?) {
        when(element) {
            is ResourceModel -> invalidate()
            is IActiveContext<*, *> -> invalidate(element)
            is Class<*> -> invalidate(element)
            is HasMetadata -> invalidate(element)
        }
    }

    private fun invalidate() {
        contexts.closeCurrent()
        contexts.allContexts.clear()
        observable.fireModified(this)
    }

    private fun invalidate(context: IActiveContext<out HasMetadata, out KubernetesClient>) {
        context.invalidate()
        observable.fireModified(context)
    }

    private fun invalidate(kind: Class<*>) {
        val hasMetadataClass = kind as? Class<HasMetadata> ?: return
        contexts.current?.invalidate(hasMetadataClass) ?: return
        observable.fireModified(hasMetadataClass)
    }

    private fun invalidate(resource: HasMetadata) {
        contexts.current?.invalidate(resource) ?: return
        observable.fireModified(resource)
    }

    private fun isNotFound(e: KubernetesClientException): Boolean {
        return e.code == 404
    }

}