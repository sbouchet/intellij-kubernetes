/*******************************************************************************
 * Copyright (c) 2022 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.kubernetes.model.resource

import io.fabric8.kubernetes.api.model.HasMetadata

class ResourceIdentifier(val resource: HasMetadata) {

    val name: String?
        get() {
            return resource.metadata.name
        }

    val generateName: String?
        get() {
            return resource.metadata.generateName
        }

    val namespace: String
        get() {
            return resource.metadata.namespace
        }

    private val resourceKind by lazy {
        ResourceKind.create(resource)
    }

    val kind: String
        get() {
            return resourceKind.kind
        }
    val version: String
        get() {
            return resourceKind.version
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResourceIdentifier

        if (name != other.name) return false
        if (generateName != other.generateName) return false
        if (namespace != other.namespace) return false
        if (kind != other.kind) return false
        if (version != other.version) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + (generateName?.hashCode() ?: 0)
        result = 31 * result + namespace.hashCode()
        result = 31 * result + kind.hashCode()
        result = 31 * result + version.hashCode()
        return result
    }

}