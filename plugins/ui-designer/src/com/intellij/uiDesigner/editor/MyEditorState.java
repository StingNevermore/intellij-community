/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.editor;

import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

final class MyEditorState implements FileEditorState{
  private final transient long myDocumentModificationStamp; // should not be serialized
  private final String[] mySelectedComponentIds;

  MyEditorState(final long modificationStamp, final String @NotNull [] selectedComponents){
    myDocumentModificationStamp = modificationStamp;
    mySelectedComponentIds = selectedComponents;
  }

  public String[] getSelectedComponentIds(){
    return mySelectedComponentIds;
  }

  @Override
  public boolean equals(final Object o){
    if (this == o) return true;
    if (!(o instanceof MyEditorState state)) return false;

    if (myDocumentModificationStamp != state.myDocumentModificationStamp) return false;
    if (!Arrays.equals(mySelectedComponentIds, state.mySelectedComponentIds)) return false;

    return true;
  }

  @Override
  public int hashCode(){
    return Long.hashCode(myDocumentModificationStamp);
  }

  @Override
  public boolean canBeMergedWith(@NotNull FileEditorState otherState, @NotNull FileEditorStateLevel level) {
    return otherState instanceof MyEditorState;
  }
}
