// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 */
public final class JavaAwareFilter {
  private JavaAwareFilter() { }

  public static Filter METHOD(final @NotNull Project project, final @NotNull GlobalSearchScope searchScope) {
    return new Filter() {
      @Override
      public boolean shouldAccept(final AbstractTestProxy test) {
        Location location = test.getLocation(project, searchScope);
        return location instanceof MethodLocation ||
               location instanceof PsiLocation && location.getPsiElement() instanceof PsiMethod;
      }
    };
  }
}