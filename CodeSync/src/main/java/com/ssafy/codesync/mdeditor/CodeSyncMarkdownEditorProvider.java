package com.ssafy.codesync.mdeditor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class CodeSyncMarkdownEditorProvider implements FileEditorProvider {

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return "md".equalsIgnoreCase(file.getExtension()); // 기본 마크다운 파일만 인식
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new CodeSyncMarkdownEditor(file);
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return "CodeSyncMarkdownEditor";
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR; // 기본 에디터보다 우선 표시
  }
}
