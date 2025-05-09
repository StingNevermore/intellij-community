// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.conda

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.registry.Registry
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.PythonPackageManagerProvider
import com.jetbrains.python.packaging.pip.PipPythonPackageManager
import com.jetbrains.python.sdk.conda.isConda
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class CondaPackageManagerProvider : PythonPackageManagerProvider {
  override fun createPackageManagerForSdk(project: Project, sdk: Sdk): PythonPackageManager? =
    if (sdk.isConda()) createCondaPackageManager(project, sdk) else null

  private fun createCondaPackageManager(project: Project, sdk: Sdk): PythonPackageManager =
    if (Registry.`is`("python.packaging.conda.chain.installation")) {
      CompositePythonPackageManager(project, sdk, listOf(CondaPackageManager(project, sdk), PipPythonPackageManager(project, sdk)))
    }
    else {
      CondaPackageManager(project, sdk)
    }
}