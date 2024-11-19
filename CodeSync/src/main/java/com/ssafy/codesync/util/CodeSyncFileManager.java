package com.ssafy.codesync.util;

import com.google.gson.Gson;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import com.jcraft.jsch.*;
import com.ssafy.codesync.state.User;
import com.ssafy.codesync.state.UserInfo;
import com.ssafy.codesync.websocket.IntelliJChangeBatcher;
import com.ssafy.codesync.websocket.MyWebSocketServer;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.swing.*;

import org.jetbrains.annotations.NotNull;

// 서버의 파일을 SFTP 방식을 통해 로컬로 가져오고,
// 로컬로 옮긴 파일을 VirtualFile을 통해 에디터로 여는 기능을 구현
public class CodeSyncFileManager {

    private final String rootDirectory = "CodeSync";
    private final Project project;
    private final MyWebSocketServer server;
    private final FileEditorManager fileEditorManager;
    private UserInfo userInfo = ServiceManager.getService(UserInfo.class);
    private static final Map<VirtualFile, IntelliJChangeBatcher> batchers = new HashMap<>();
//    private boolean isUserInput = false; // 플래그 추가
    private final Map<VirtualFile, DocumentListener> documentListeners = new HashMap<>();
    private final Map<VirtualFile, Boolean> userInputFlags = new HashMap<>();
//    private DocumentListener documentListener;
    private final Map<VirtualFile, Process> processes = new HashMap<>();

    // ** plugin 설치 시
    private String pluginPath;
    public CodeSyncFileManager(Project project, MyWebSocketServer server, String pluginPath) {
        this.project = project;
        this.server = server;
        this.fileEditorManager = FileEditorManager.getInstance(project);
        this.pluginPath = pluginPath + "\\y_websocket.js";

        // Add FileEditorManagerListener
        fileEditorManager.addFileEditorManagerListener(new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                //TODO 작성
                attachKeyListenerToEditor(file);
                setupDocumentListener(file);
            }

            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                //TODO 작성
                cleanupOnFileClose(file);
            }
        });
    }

    // plugin 설치 시
//    public CodeSyncFileManager(Project project, Session session, MyWebSocketServer server) {
//        this.project = project;
//        this.session = session;
//        this.server = server;
//        this.fileEditorManager = FileEditorManager.getInstance(project);
//    }

    // 리스너를 통해 선택된 파일을 downloadFile 메서드를 통해 로컬에 저장 후, 에디터 형식으로 열기
    public void downloadFileAndOpenInEditor(String fileName, String username, String serverIp) {
        try {
            String remoteFilePath = "/home/" + username + "/" + rootDirectory + "/" + fileName;
            System.out.println("1 - 서버의 파일을 로컬에 다운로드 Start");
            File localFile = downloadFile(remoteFilePath, serverIp);
            System.out.println("2 - 서버의 파일을 로컬에 다운로드 End");
            System.out.println("3 - 로컬 파일을 버츄얼 파일로 가져오기 Start");
            VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localFile);
            System.out.println("4 - 로컬 파일을 버츄얼 파일로 가져오기 End");


            if (virtualFile != null) {
                virtualFile.refresh(true, false);  // 강제 새로 고침
                System.out.println("5 - 에디터 열기 Start");
                ApplicationManager.getApplication().invokeLater(() -> {
                    openEditorForFileType(virtualFile); // 서버 파일만 에디터에서 엶
                });
                System.out.println("5 - 에디터 열기 End");

                startProcessWithJson(localFile, serverIp, fileName, virtualFile); // Process 실행
            } else  {
                System.out.println("4 - 버츄얼 파일 가져오기 실패");
                throw new RuntimeException("Failed to create VirtualFile for: " + localFile.getPath());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }


    private void openEditorForFileType(VirtualFile virtualFile) {
        if ("md".equalsIgnoreCase(virtualFile.getExtension())) {
            // .md 확장자를 가진 마크다운 파일에 한정하여 커스텀 에디터를 불러오기
            FileEditorProvider[] providers = FileEditorProviderManager.getInstance()
                    .getProviders(project, virtualFile);
            for (FileEditorProvider provider : providers) {
                if (provider.getEditorTypeId().equals("CodeSyncMarkdownEditor")) {
                    fileEditorManager.openEditor(new OpenFileDescriptor(project, virtualFile), true);
                    break;
                }
            }
        } else {
            fileEditorManager.openFile(virtualFile, true);
        }

        // 일정 시간마다 자동으로 서버에 업로드
        SwingUtilities.invokeLater(() -> {
            startFileUploadThread(virtualFile);
        });
    }

    private void attachKeyListenerToEditor(VirtualFile file) {
        Editor editor = fileEditorManager.getSelectedTextEditor();
        if (editor != null) {
            editor.getContentComponent().addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    System.out.println("[ intellij (key) ] keyPressed!");
                    userInputFlags.put(file, true); // 파일별로 상태 저장
                    e.consume();
//                    int caretPosition = editor.getCaretModel().getOffset();  // 현재 커서 위치
//                    editor.getCaretModel().moveToOffset(caretPosition);
                }
            });
        } else {
            System.out.println("[ intellij (key) ] editor 생성 안됨 -> keyListener 생성 안됨 -> 입력 감지 못함.");
        }
    }

    private void setupDocumentListener(VirtualFile file) {
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
            userInputFlags.put(file, false); // 파일에 대한 userInputFlag 초기화

            DocumentListener listener = new DocumentListener() {
                @Override
                public void documentChanged(@NotNull DocumentEvent event) /* {
                    Boolean isUserInput = userInputFlags.getOrDefault(file, false);
                    IntelliJChangeBatcher batcher = batchers.get(file);

                    if (isUserInput) {
                        System.out.println("[ intellij ] 사용자 입력으로 문서가 변경되었습니다.");
                        if (batcher != null) {
                            batcher.onDocumentChange(event);
                        }
                        userInputFlags.put(file, false);
                    } else {
                        System.out.println("[ intellij ] 시스템에 의해 문서가 변경되었습니다.");
                    }
                } */
                {
                    Boolean isUserInput = userInputFlags.getOrDefault(file, false);
                    IntelliJChangeBatcher batcher = batchers.get(file);
                    if (batcher == null) {
                        batcher = new IntelliJChangeBatcher(server);
                        batchers.put(file, batcher);
                    } else {
                        // Ensure batcher restarts if needed
                        if (!batcher.isBatchingActive) {
                            batcher.startBatching();
                        }
                        System.out.println("[ intellij ] Reusing existing batcher." + batchers);
                    }

                    if (isUserInput || (!server.socketFlag && (event.getOldLength() > event.getNewLength() || event.getNewFragment().toString().equals("\n")))) {
                        System.out.println("[ intellij (key) ] 키보드 입력에 의한 문서 변경. newText: " + event.getNewFragment().toString());
                        System.out.println("\\" + event.getNewFragment().toString());
                        if (batcher != null) {
                            batcher.onDocumentChange(event);
                        }
                        userInputFlags.put(file, false);
                    } else {
                        System.out.println("[ intellij (key) ] 시스템 또는 다른 방법에 의한 문서 변경.: " + event.getNewFragment().toString().equals("\n"));
                    }
                }
            };
            File localFile = new File(file.getPath());
            server.init(document, fileEditorManager, localFile);
            document.addDocumentListener(listener);
            documentListeners.put(file, listener);

            // 해당 파일에 대한 batcher 설정
            setupBatcher(file);
        }
    }

    private void setupBatcher(VirtualFile file) {
        if (!batchers.containsKey(file)) {
            IntelliJChangeBatcher batcher = new IntelliJChangeBatcher(server);
            batchers.put(file, batcher);
        }
    }

    private void cleanupOnFileClose(VirtualFile file) {
        // Batcher 정리
        IntelliJChangeBatcher batcher = batchers.remove(file);
        if (batcher != null) {
            batcher.dispose();
            System.out.println("Batcher disposed for file: " + file.getPath());
        }

        // DocumentListener 정리
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
            DocumentListener listener = documentListeners.remove(file);
            if (listener != null) {
                document.removeDocumentListener(listener);
                System.out.println("DocumentListener removed for file: " + file.getPath());
            }
        }

        Process finishProcess = processes.get(file);
        finishProcess.destroy();
        processes.remove(file);

        // 로컬 파일 업로드 및 삭제
        File localFile = new File(file.getPath());
        String[] dirs = localFile.getParent().split("\\\\");
        User user = userInfo.getUserByServerIP(dirs[dirs.length-1]);
        String remoteFilePath = "/home/" + user.getServerOption() + "/" + rootDirectory + "/" + file.getName();
        try {
            boolean uploadSuccess = uploadFile(remoteFilePath, file.getParent().getName(), localFile);
            if (uploadSuccess && localFile.exists() && localFile.delete()) { // && localFile.delete()
                System.out.println("Local file deleted: " + localFile.getPath());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // UserInputFlag 정리
        userInputFlags.remove(file);
        System.out.println("UserInputFlag removed for file: " + file.getPath());
    }


    // SFTP를 통해 서버에 파일 내용을 가져와 로컬에 저장
    public File downloadFile(String remoteFilePath, String serverIp) throws JSchException, SftpException {
        User user = userInfo.getUserByServerIP(serverIp);

        JSch jsch = new JSch();
        jsch.addIdentity(user.getPemKeyPath());
        Session session = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
        channelSftp.connect();

        String tmpDir = System.getProperty("java.io.tmpdir") + "\\" + rootDirectory + "\\" + serverIp;
        File localDir = new File(tmpDir);
        if (!localDir.exists()) localDir.mkdirs();
        localDir.deleteOnExit();

        File localFile = new File(tmpDir, new File(remoteFilePath).getName());
//         localFile.deleteOnExit(); // JVM 종료 시 삭제 예약

        // 서버 파일 다운로드
        // channelSftp.get(remoteFilePath, localFile.getAbsolutePath());
        System.out.println("remoteFilePath: " +remoteFilePath);
        InputStream inputStream = channelSftp.get(remoteFilePath);
        try (FileOutputStream outputStream = new FileOutputStream(localFile)) {
            byte[] buffer = new byte[1024];
            int readCount;
            while ((readCount = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, readCount);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error downloading file: " + e.getMessage(), e);
        } finally {
            channelSftp.disconnect();
            session.disconnect();
        }

        System.out.println("DOWNLOAD SUCCESS!!!");
        return localFile;
    }

//    public long getFileSize(String remoteFilePath) throws JSchException, SftpException {
//        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
//        channelSftp.connect();
//        long fileSize = channelSftp.lstat(remoteFilePath).getSize();
//        channelSftp.disconnect();
//        return fileSize;
//    }

//    private void handleDocumentChange(@NotNull DocumentEvent event, MyWebSocketServer server, String fileName) {
//        ApplicationManager.getApplication().executeOnPooledThread(() -> {
//            ReadAction.run(() -> {
//                String content = event.getDocument().getText();
//                // String content = fileName + "@@@" + event.getDocument().getText();
//                server.broadcast(content);
//            });
//        });
//    }

    private void startProcessWithJson(File localFile, String serverIp, String fileName, VirtualFile virtualFile) {
        try {
            // 파일 내용 읽기
            String fileContent = new String(Files.readAllBytes(localFile.toPath())).trim();

            // JSON 데이터 준비
            Map<String, String> jsonMap = new HashMap<>();
            jsonMap.put("fileContent", fileContent);
            jsonMap.put("host", serverIp);
            jsonMap.put("fileName", fileName);

            // JSON 파일 생성
            String jsonArgs = new Gson().toJson(jsonMap);
            Path jsonTempFile = Files.createTempFile("zargs", ".json");
            jsonTempFile.toFile().deleteOnExit(); // JVM 종료 시 임시 파일 삭제 예약
            Files.write(jsonTempFile, jsonArgs.getBytes(StandardCharsets.UTF_8));

            // ProcessBuilder 준비
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "node", // Node.js 실행 명령어
                    pluginPath, // 실행할 스크립트 경로
                    jsonTempFile.toString() // JSON 파일 경로
            );

            // 작업 디렉토리 설정 (필요시)
            processBuilder.directory(new File(pluginPath).getParentFile());

            // 프로세스 실행
            Process process = processBuilder.start();
            processes.put(virtualFile, process);
            System.out.println("[ intellij ] Process 실행");

            // 프로세스 출력 읽기
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println("[ Process Output ] " + line);
                }
            }

            // 종료 코드 확인
            int exitCode = process.waitFor();
            System.out.println("[ intellij ] Process 종료 코드: " + exitCode);

            if (exitCode != 0) {
                System.err.println("[ intellij ] Process 실행 중 오류 발생");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("[ intellij ] Process 실행 실패");
        }
    }


    private void startFileUploadThread(VirtualFile virtualFile) {
        new Thread(() -> {
            while (fileEditorManager.isFileOpen(virtualFile)) {
                String filePath = virtualFile.getPath();
                String serverIp = virtualFile.getParent().getName();
                File localFile = new File(filePath);

                try {
                    System.out.println(localFile.getName() + " 파일 업로드 타이머 설정!");
                    Thread.sleep(60000); // 1분 60000
                    User user = userInfo.getUserByServerIP(serverIp);
                    JSch jsch = new JSch();
                    jsch.addIdentity(user.getPemKeyPath());
                    Session session = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
                    session.setConfig("StrictHostKeyChecking", "no");
                    session.connect();

                    ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
                    channelSftp.connect();

                    String remoteFilePath = "/home/" + user.getServerOption() + "/" + rootDirectory + "/" + localFile.getName();
                    channelSftp.put(localFile.getAbsolutePath(), remoteFilePath);

                    channelSftp.disconnect();
                    session.disconnect();

                    System.out.println(localFile.getName() + " 파일 업로드 타이머 완료!");
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            }
        }).start();
    }

    public boolean uploadFile(String remoteFilePath, String serverIp, File localFile) throws JSchException, SftpException {
        try {
            User user = userInfo.getUserByServerIP(serverIp);
            JSch jsch = new JSch();
            jsch.addIdentity(user.getPemKeyPath());
            Session session = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();

            channelSftp.put(localFile.getAbsolutePath(), remoteFilePath);

            channelSftp.disconnect();
            session.disconnect();

            System.out.println(localFile.getName() + " 파일 업로드 완료!");
            return true;
        } catch (Exception e1) {
            e1.printStackTrace();
            return false;
        }
    }

}

