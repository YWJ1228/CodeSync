package com.ssafy.codesync.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.ssafy.codesync.state.User;
import com.ssafy.codesync.state.UserInfo;
import com.ssafy.codesync.util.FileTransferProgressMonitor;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Objects;

public class UploadEditorToRemoteRepository extends AnAction {
    private UserInfo userInfo = ServiceManager.getService(UserInfo.class);
    private final String rootDirectory = "vscode-test";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile virtualFile = editor.getVirtualFile();
        UserInfo userInfo = ServiceManager.getService(UserInfo.class);

        if (virtualFile == null) {
            return;
        }
        // 파일의 경로를 가져옵니다.
        String filePath = virtualFile.getPath();
        System.out.println("파일 경로: " + filePath);

        // 파일 이름을 가져옵니다.
        String fileName = virtualFile.getName();
        System.out.println("파일 이름: " + fileName);

        // 파일의 부모 디렉터리 경로를 가져옵니다.
        String parentPath = virtualFile.getParent().getPath();
        String serverIp = virtualFile.getParent().getName();
        System.out.println("부모 디렉터리 경로: " + parentPath);
        System.out.println("serverIP: " + serverIp);

        File localFile = new File(filePath);

        // SessionFileStorage sessionFileStorage = SessionFileStorage.getInstance();
        // String name = sessionFileStorage.getName();
        // File localFile = sessionFileStorage.getFile();

        Project project = e.getProject();
        if (project == null) {
            return; // 프로젝트가 없으면 리턴
        }

        // 로컬 가상 파일 저장 먼저 (파일 캐시 충돌 방지)
        // VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(localFile);
        if (virtualFile != null) {
            FileDocumentManager.getInstance().saveDocument(Objects.requireNonNull(FileDocumentManager.getInstance().getDocument(virtualFile)));
        }

        ProgressManager.getInstance().
                run(new Task.Backgroundable(project, "Uploading " + localFile, true) {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setText("Uploading " + localFile + "...");

                        try {
                            User user = userInfo.getUserByServerIP(serverIp);
                            JSch jsch = new JSch();
                            jsch.addIdentity(user.getPemKeyPath());
                            Session session = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
                            session.setConfig("StrictHostKeyChecking", "no");
                            session.connect();

                            // 파일 전송 진행률 화면 표시
                            long fileSize = localFile.length(); // 서버로 업로드 할 로컬 파일 크기 확인
                            FileTransferProgressMonitor progressMonitor = new FileTransferProgressMonitor("Upload", indicator, fileSize);
                            progressMonitor.showProgressFrame();

                            ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
                            channelSftp.connect();

                            String remoteFilePath = "/home/" + user.getServerOption() + "/" + rootDirectory + "/" + localFile.getName();
                            channelSftp.put(localFile.getAbsolutePath(), remoteFilePath, progressMonitor);

                            channelSftp.disconnect();
                            session.disconnect();

                            // 캐시 새로고침으로 변경 사항 반영
                            VirtualFile refreshedFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localFile);
                            if (refreshedFile != null) {
                                refreshedFile.refresh(true, false); // 파일을 강제로 새로고침하여 캐시 문제 해결
                            }
                        } catch (
                                Exception e1) {
                            e1.printStackTrace();
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