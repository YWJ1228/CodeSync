package com.ssafy.codesync.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.ssafy.codesync.util.FileTransferProgressMonitor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public class DownloadEditorToLocalDisk  extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        System.out.println("DownloadEditorToLocalDisk call!!");

        Project project = e.getProject();
        if (project == null) {
            return;
        }

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile virtualFile = Objects.requireNonNull(editor).getVirtualFile();
        if (virtualFile == null) {
            return;
        }

        FileDocumentManager.getInstance().saveDocument(Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(virtualFile)));
        File localFile = new File(virtualFile.getPath());

        // 파일 경로 선택창 열기
        FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false);
        VirtualFile targetDirectory = FileChooser.chooseFile(descriptor, project, null);

        if (targetDirectory == null) {
            Messages.showErrorDialog(project, "No destination directory has been selected.", "Error");
            return;
        }
        File targetFile = new File(targetDirectory.getPath(), localFile.getName());

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Downloading " + localFile, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setText("Downloading " + localFile + "...");

                // 파일 전송 진행률 화면 표시
                long fileSize = localFile.length(); // 다운로드 할 파일 크기 확인
                FileTransferProgressMonitor progressMonitor = new FileTransferProgressMonitor("Download", indicator, fileSize);
                progressMonitor.showProgressFrame();

                // 파일 복사
                try {
                    Files.copy(localFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    JOptionPane.showMessageDialog(null, "Download Success!");
                } catch (IOException ex) {
                    indicator.cancel();
                    Messages.showErrorDialog(project, "An error occurred while downloading the file." + ex.getMessage(), "Error");
                } finally {
                    progressMonitor.closeProgressFrame();
                }
            }
        });
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT; // EDT 또는 BGT 선택
    }

    @Override
    public void update(AnActionEvent e) {
        // 현재 에디터 가져오기
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor != null) {
            // 현재 에디터가 특정 툴 윈도우에 있는지 확인
            ToolWindow toolWindow = ToolWindowManager.getInstance(Objects.requireNonNull(e.getProject())).getToolWindow("CodeSync");
            if (toolWindow != null && toolWindow.isVisible()) {
                // 특정 조건을 만족할 때만 액션을 활성화
                e.getPresentation().setVisible(true);
                return;
            }
        }
        // 조건을 만족하지 않으면 숨김
        e.getPresentation().setVisible(false);
    }
}