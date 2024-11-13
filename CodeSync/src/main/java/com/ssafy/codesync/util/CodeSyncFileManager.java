package com.ssafy.codesync.util;

import com.google.gson.Gson;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
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
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.ssafy.codesync.state.User;
import com.ssafy.codesync.state.UserInfo;
import com.ssafy.codesync.websocket.MyWebSocketServer;

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
import javax.swing.JOptionPane;

import org.jetbrains.annotations.NotNull;

// 서버의 파일을 SFTP 방식을 통해 로컬로 가져오고,
// 로컬로 옮긴 파일을 VirtualFile을 통해 에디터로 여는 기능을 구현
public class CodeSyncFileManager {

    private final String rootDirectory = "vscode-test";
    private final Project project;
    private final Session session;
    private final MyWebSocketServer server;
    private File localFile;
    private final FileEditorManager fileEditorManager;
    private Process process;
    private UserInfo userInfo = ServiceManager.getService(UserInfo.class);

    public CodeSyncFileManager(Project project, Session session, MyWebSocketServer server) {
        this.project = project;
        this.session = session;
        this.server = server;
        this.fileEditorManager = FileEditorManager.getInstance(project);
    }

    // 리스너를 통해 선택된 파일을 downloadFile 메서드를 통해 로컬에 저장 후, 에디터 형식으로 열기
    public void downloadFileAndOpenInEditor(String fileName, String name, String username, String serverIp) {
        try {
            String remoteFilePath = "/home/" + username + "/" + rootDirectory + "/" + fileName;
            System.out.println("1 - 서버의 파일을 로컬에 다운로드 Start");
            localFile = downloadFile(remoteFilePath, serverIp);
            System.out.println("2 - 서버의 파일을 로컬에 다운로드 End");

            if (localFile != null) {
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
                    // 에디터가 닫힐 때 로컬 파일 삭제
                    MessageBusConnection connection = project.getMessageBus().connect();
                    File finalLocalFile = localFile;
                    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
                        @Override
                        public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                            if (file.equals(virtualFile)) {
                                // 에디터가 닫히면 로컬 파일 삭제
                                if (finalLocalFile.exists()) {
                                    finalLocalFile.delete();
                                }
                                process.destroy();
                                connection.disconnect(); // 리스너 연결 해제
                            }
                        }
                    });
                } else {
                    JOptionPane.showMessageDialog(null, "Virtual File Error");
                    System.out.println("5-2 Virtual File Error");
                    return;
                }
                //                });
            } else {
                JOptionPane.showMessageDialog(null, "Download Error");
                System.out.println("3-2 Download Error");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "Cannot open the file.");
            return;
        }

        session.disconnect();
        System.out.println("6-start websocket");

        // ** Document 생성
        Document document = EditorFactory.getInstance().createDocument("");

        try {
            // ** 문자열로 변환하여 Document에 삽입
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            // 문자열로 변환하여 Document에 삽입
            String content = byteArrayOutputStream.toString(StandardCharsets.UTF_8);
            WriteCommandAction.runWriteCommandAction(project, () -> {
                document.setText(content);
            });

            ApplicationManager.getApplication().invokeLater(() -> {
            VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localFile);
            Document doc = FileDocumentManager.getInstance().getDocument(virtualFile);
            System.out.println("야 doc.getText() 출력할거다 -> " + doc.getText());
            System.out.println("야 이번엔 virtualFile 출력할거야 -> " + virtualFile);
            server.init(doc, fileEditorManager, localFile);
                doc.addDocumentListener(new DocumentListener() {
                    @Override
                    public void documentChanged(@NotNull DocumentEvent event) {
                        handleDocumentChange(event, server, fileName);
                    }
                });
            });

            // ** 변경할 것 **
            // String scriptPath = "C:\\Users\\SSAFY\\Desktop\\intellij\\S11P31A101\\vscode-extension\\codesync\\y_websocket.js";

            // --------------------------------------------------------------------------
            // 팀 서버 등록 시 입력 받은 웹소켓 경로를 가져옴
            User user = userInfo.getUserByServerIP(serverIp);
            String scriptPath = user.getWebSocketPath();
            System.out.println("websocket path: " + scriptPath);

            // node_modules 파일이 있는 상위 경로 이동
            String parentPath = "";
            if (scriptPath != null) {
                int lastDescriptorIndex = scriptPath.lastIndexOf("\\");
                if (lastDescriptorIndex != -1) {
                    parentPath = scriptPath.substring(0, lastDescriptorIndex);
                    System.out.println("websocket's parent path: " + parentPath);

                    File nodeModules = new File(parentPath, "node_modules");
                    if (!nodeModules.exists()) {  // node_modules가 없을 때만 실행
                        try {
                            Process npmProcess = new ProcessBuilder("cmd.exe", "/c", "npm install")
                                    .directory(new File(parentPath))
                                    .start();

                            int exitCode = npmProcess.waitFor();
                            if (exitCode != 0) {
                                System.out.println("npm install 실패, 종료 코드: " + exitCode);
                            } else {
                                System.out.println("npm install 성공.");
                            }
                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("node_modules already exists, skipping npm install.");
                    }
                }
            }
            // --------------------------------------------------------------------------

            String fileContent = new String(Files.readAllBytes(localFile.toPath())).trim();

            // ** ProcessBuilder 시작
            Map<String, String> jsonMap = new HashMap<>();
            jsonMap.put("fileContent", fileContent);
            jsonMap.put("host", serverIp); // 변경할 것(ip주소로)
            jsonMap.put("fileName", fileName);  // 변경할 것

            String jsonArgs = new Gson().toJson(jsonMap);

            // JSON 파일 생성 및 내용 쓰기
            Path jsonTempFile = Files.createTempFile("zargs", ".json");
            Files.write(jsonTempFile, jsonArgs.getBytes(StandardCharsets.UTF_8));
            jsonTempFile.toFile().deleteOnExit();

            Thread processThread = new Thread(() -> {
                try {
                    // ProcessBuilder에 JSON 파일 경로 전달
                    ProcessBuilder processBuilder = new ProcessBuilder(
                            // "C:\\Program Files\\GraalVM\\graaljs-jvm-24.1.1-windows-amd64\\graaljs-24.1.1-windows-amd64\\bin\\js.exe",
                            "node",
                            // "js",
                            scriptPath, jsonTempFile.toString());
                    // 작업 디렉토리 설정 (필요한 경우)
                    // processBuilder.directory(new File("작업_디렉토리_경로"));

                    // 프로세스 실행
                    process = processBuilder.start();
                    System.out.println("[ intellij ] process 실행");

                    // 출력 읽기 스레드 생성
                    StringBuilder outputBuilder = new StringBuilder();

                    Thread outputThread = new Thread(() -> {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                outputBuilder.append(line).append("\n");
                                System.out.println(line);
                            }
                            System.out.println("[ intellij ] 출력 : " + outputBuilder);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });

                    outputThread.start(); // 스레드 시작

                    // 프로세스 종료 코드 확인
                    int exitCode = process.waitFor();
                    // listener.close();
                    System.out.println("[ intellij ] Exit Code: " + exitCode);
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace(); // 예외 처리
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            processThread.start();
            processThread.join();
            // 끝

        } catch (Exception e) {
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
                    return;
                }
            }
        } else {
            fileEditorManager.openFile(virtualFile, true);
        }
    }

    // SFTP를 통해 서버에 파일 내용을 가져와 로컬에 저장
    public File downloadFile(String remoteFilePath, String serverIp)
            throws JSchException, SftpException {
        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
        channelSftp.connect();

        String tmpDir = System.getProperty("java.io.tmpdir") + "\\" + rootDirectory + "\\" + serverIp;
        File tmplocalFile = new File(tmpDir);
        if (!tmplocalFile.exists()) {
            tmplocalFile.mkdirs();
        }
        tmplocalFile.deleteOnExit();

        File localFile = new File(tmpDir, new File(remoteFilePath).getName());
        localFile.deleteOnExit(); // JVM 종료 시 삭제 예약

        // 서버 파일 다운로드
        // channelSftp.get(remoteFilePath, localFile.getAbsolutePath());
        InputStream inputStream = channelSftp.get(remoteFilePath);
        try (FileOutputStream outputStream = new FileOutputStream(localFile)) {
            byte[] buffer = new byte[1024];
            int readCount;
            while ((readCount = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, readCount);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        channelSftp.disconnect();
        System.out.println("DOWNLOAD SUCCESS!!!");
        return localFile;
    }

    public long getFileSize(String remoteFilePath) throws JSchException, SftpException {
        ChannelSftp channelSftp = (ChannelSftp) session.openChannel("sftp");
        channelSftp.connect();
        long fileSize = channelSftp.lstat(remoteFilePath).getSize();
        channelSftp.disconnect();
        return fileSize;
    }

    private void handleDocumentChange(@NotNull DocumentEvent event, MyWebSocketServer server, String fileName) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            ReadAction.run(() -> {
                String content = event.getDocument().getText();
                // String content = fileName + "@@@" + event.getDocument().getText();
                server.broadcast(content);
            });
        });
    }
}