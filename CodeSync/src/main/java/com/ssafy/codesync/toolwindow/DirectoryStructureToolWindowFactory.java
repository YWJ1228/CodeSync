package com.ssafy.codesync.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.treeStructure.Tree;
import com.jcraft.jsch.*;
import com.ssafy.codesync.form.ConnectionPanel;
import com.ssafy.codesync.state.User;
import com.ssafy.codesync.state.UserInfo;
import com.ssafy.codesync.util.CodeSyncFileManager;
import com.ssafy.codesync.websocket.MyWebSocketServer;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.regex.Pattern;

public class DirectoryStructureToolWindowFactory implements ToolWindowFactory {
    // mainPanel: CodeSync 좌측 툴바의 메인 패널
    // tabbedPane: 사용자가 추가한 각각의 서버 탭 묶음
    // tabPanel: tabbedPane에 들어가는 각각의 서버 탭
    // userInfo: 플러그인이 종료되어도 저장되는 PersistentStateComponent 데이터 (IP, PEM_KEY_PATH 등 저장)
    private JPanel mainPanel;
    private JBTabbedPane tabbedPane;
    private JPanel tabPanel;
    private UserInfo userInfo = ServiceManager.getService(UserInfo.class);
    private final String rootDirectory = "vscode-test";

    // JPanel로 입력 받은 서버 등록 정보를 받아와 저장하는 클래스
    public class PanelInfo {
        public String teamName;
        public String serverIP;
        public String pemKeyPath;
        public String serverOption;

        public PanelInfo() {
        }

        public PanelInfo(ConnectionPanel connectionPanel) {
            this.teamName = connectionPanel.getTeamName().getText();
            this.serverIP = connectionPanel.getServerIP().getText().trim();
            this.pemKeyPath = connectionPanel.getPemKeyPath().getText().trim();
            this.serverOption = connectionPanel.getServerTypeComboBox().getItem().equals("Linux") ? "ec2-user" : "ubuntu";
        }

        public PanelInfo(User user) {
            this.teamName = user.getName();
            this.serverIP = user.getServerIP();
            this.pemKeyPath = user.getPemKeyPath();
            this.serverOption = user.getServerOption();
        }
    }

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        mainPanel = new JPanel(new BorderLayout());
        tabbedPane = new JBTabbedPane();

        // PersistentStateComponent 테스트 삭제용
        // userInfo.removeAll();

        if (userInfo.getUsers().isEmpty()) {
            System.out.println("Not Exist UserInfo!!!");
            ConnectionPanel connectionPanel = new ConnectionPanel();
            connectionPanel.getConnectButton().addActionListener(e -> {
                PanelInfo panelInfo = new PanelInfo(connectionPanel);
                onShowing(project, toolWindow, panelInfo);

                // PersistentStateComponent 저장
                userInfo.addUser(new User(connectionPanel));
                System.out.println(userInfo.getUsers().size());

                tabbedPane.remove(tabbedPane.indexOfTab("New"));
            });
            tabPanel = connectionPanel;
            tabbedPane.addTab("New", tabPanel);
            mainPanel.add(tabbedPane);
        } else {
            System.out.println("Exist UserInfo!!!");
            JButton reloadButton = new JButton("Reload");
            reloadButton.addActionListener(e -> {
                mainPanel.remove(reloadButton);
                for (int i = 0; i < userInfo.getUsers().size(); i++) {
                    System.out.println(userInfo.getUsers().get(i).getServerIP());

                    // 저장된 정보 connectionPanel에 담기
                    User user = userInfo.getUsers().get(i);
                    PanelInfo panelInfo = new PanelInfo(user);

                    onShowing(project, toolWindow, panelInfo);
                }
                JOptionPane.showMessageDialog(null, "Connection Success!");
            });
            mainPanel.add(reloadButton);
        }

        toolWindow.getComponent().add(mainPanel);
    }

    // 서버 연결 - 디렉토리 구조 불러오기 + 리스너 등록(파일 열기, 디렉토리 다시 불러오기, 서버 탭 삭제 등)
    public void onShowing(Project project, ToolWindow toolWindow, PanelInfo panelInfo) {
        String name = panelInfo.teamName;
        String serverIp = panelInfo.serverIP.trim();
        String pemFilePath = panelInfo.pemKeyPath.trim();
        String username = panelInfo.serverOption;

        // 서버 탭 이름 중복 검사
        if (tabbedPane.indexOfTab(name) != -1) {
            JOptionPane.showMessageDialog(null, "The name is duplicate!");
            return;
        } else if (name.isEmpty()) {
            JOptionPane.showMessageDialog(null, "Please specify a name!");
            return;
        }

        try {
            JSch jsch = new JSch();
            jsch.addIdentity(pemFilePath);

            Session session = jsch.getSession(username, serverIp, 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

//            JOptionPane.showMessageDialog(null, "Connection Success!");

            tabPanel = new JPanel(new BorderLayout());
            tabPanel.setLayout(new BoxLayout(tabPanel, BoxLayout.Y_AXIS));

            // 연결된 서버에서 불러올 루트 디렉토리 설정
            JTree tree = makeDirectoryStructure(project, session, username, serverIp, name);
            JScrollPane scrollPane = new JBScrollPane(tree);
            tabPanel.add(scrollPane);

            // 불러온 Tree 형식 디렉토리 구조에서 파일 클릭 시, 해당 파일 에디터를 열 수 있도록 리스너 설정
            openFileListener(tree, project, serverIp);

            JPanel buttonPanel = new JPanel(new FlowLayout());

            // 서버 탭의 이름을 수정하기 위한 리스너가 등록된 버튼 생성
            JButton renameTabNameButtonPanel = renameServerTabListener(project, name);
            buttonPanel.add(renameTabNameButtonPanel);

            // 서버의 디렉토리를 다시 불러오는 새로고침 리스너가 등록된 버튼 생성
            JButton refreshButtonPanel = refreshDirectoryStructureListener(scrollPane, project, serverIp, name);
            buttonPanel.add(refreshButtonPanel);

            // 서버 탭을 삭제하기 위한 리스너가 등록된 버튼 생성
            JButton deleteButtonPanel = deleteServerTabListener(project, toolWindow, name, serverIp);
            buttonPanel.add(deleteButtonPanel);

            // 리스너가 등록된 버튼을 해당 서버 탭 패널에 추가
            tabPanel.add(buttonPanel);
            // 서버 탭 패널을 탭 묶음 패널에 추가
            tabbedPane.addTab(name, tabPanel);

            // 새롭게 서버 탭을 추가할 수 있도록 리스너가 등록된 버튼 생성
            JButton addButton = addServerTabListener(project, toolWindow);

            mainPanel.add(addButton, BorderLayout.SOUTH);
            mainPanel.add(tabbedPane);
            mainPanel.revalidate();
            mainPanel.repaint();

            session.disconnect();

        } catch (Exception jschE) {
            jschE.printStackTrace();
        }
    }

    public JTree makeDirectoryStructure(Project project, Session session, String username, String serverIp, String name) {
        try {
            String directoryStructure = getDirectoryStructure(session, "/home/" + username + "/" + rootDirectory);
            return createFileTree(directoryStructure, session, project, serverIp, name);
        } catch (JSchException jSchException) {
            jSchException.printStackTrace();
        }
        return null;
    }

    public JTree remakeDirectoryStructure(Project project, String serverIp, String name) {
        try {
            User user = userInfo.getUserByServerIP(serverIp);
            JSch jsch = new JSch();
            jsch.addIdentity(user.getPemKeyPath());
            Session session = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            String directoryStructure = getDirectoryStructure(session, "/home/" + user.getServerOption() + "/" + rootDirectory);
            session.disconnect();
            return createFileTree(directoryStructure, session, project, serverIp, name);
        } catch (JSchException jSchException) {
            jSchException.printStackTrace();
        }
        return null;
    }

    // 접속 서버에 "ls -p" 명령어를 통한 출력 결과 불러오기
    public String getDirectoryStructure(Session session, String remoteDir) throws JSchException {
        Channel channel = session.openChannel("exec");
        ((ChannelExec) channel).setCommand("ls -p " + remoteDir);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        channel.setOutputStream(outputStream);

        channel.connect();
        while (!channel.isClosed()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        channel.disconnect();

        return outputStream.toString();
    }

    // 불러올 Tree 형식 디렉토리 구조 populateTree 메서드를 통해 정리 후, return
    public Tree createFileTree(String directoryStructure, Session session, Project project, String serverIp, String name) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(serverIp + "'s CodeSync Directory");
        populateTree(directoryStructure, rootNode);
        Tree tree = new Tree(rootNode);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // 팝업 메뉴 설정
        JPopupMenu popupMenuDirectory = new JPopupMenu();
        JMenuItem refreshDirectory = new JMenuItem("Refresh Directory");
        JMenuItem createFile = new JMenuItem("Create File");

        JPopupMenu popupMenuFile = new JPopupMenu();
        JMenuItem openFile = new JMenuItem("Open");
        JMenuItem renameFile = new JMenuItem("Rename");
        JMenuItem deleteFile = new JMenuItem("Delete");

        // 우클릭 시 팝업 메뉴 띄우기
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            private void showPopup(MouseEvent e) {
                int selRow = tree.getRowForLocation(e.getX(), e.getY());
                TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                // DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
                if (selRow == 0) {
                    // System.out.println("selRow: "+selRow);
                    // System.out.println("selPath: "+selPath);
                    popupMenuDirectory.show(e.getComponent(), e.getX(), e.getY());
                } else if (selPath != null) {
                    // System.out.println("selRow: "+selRow);
                    // System.out.println("selPath: "+selPath);
                    tree.setSelectionPath(selPath);
                    popupMenuFile.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        // 디렉토리 노드에 액션 리스너 생성 - 파일 새로고침
        refreshDirectory.addActionListener(e -> {
            System.out.println("refreshDirectory call!!!");

            JTree newTree = remakeDirectoryStructure(project, serverIp, name);
            openFileListener(newTree, project, serverIp);

            JScrollPane scrollPane = getScrollPaneByName(name);
            scrollPane.setViewportView(newTree); // JScrollPane의 뷰포트를 새 트리로 설정
            scrollPane.revalidate(); // 레이아웃 재계산
            scrollPane.repaint(); // 패널 다시 그리기

            /*
            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
            for (VirtualFile virtualFile : fileEditorManager.getOpenFiles()) {
                fileEditorManager.closeFile(virtualFile);
            }
            */

            JOptionPane.showMessageDialog(null, "Refresh Success!");
        });

        // 디렉토리 노드에 액션 리스너 생성 - 파일 생성
        createFile.addActionListener(e -> {
            System.out.println("createFile call!!!");

            String pattern = "^[^<>:\"/|?*]*\\.(md|js|ts|java|txt)$";
            String fileName = JOptionPane.showInputDialog("생성할 파일명을 입력하세요:");
            fileName = fileName.trim();
            if (fileName.isEmpty()) {
                JOptionPane.showMessageDialog(null, "파일 이름을 입력하세요.");
                return;
            }
            else if(!Pattern.matches(pattern, fileName)) {
                JOptionPane.showMessageDialog(null, "파일명이 올바르지 않습니다.");
                return;
            }
            else if(!(fileName.endsWith(".md") || fileName.endsWith(".js") || fileName.endsWith(".ts") || fileName.endsWith(".java") || fileName.endsWith(".txt"))) {
                JOptionPane.showMessageDialog(null, "현재 .md / .js / .ts / .java / .txt만 지원합니다.");
                return;
            }

            try {
                User user = userInfo.getUserByServerIP(serverIp);
                JSch jsch = new JSch();
                jsch.addIdentity(user.getPemKeyPath());
                Session createFileSession = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
                createFileSession.setConfig("StrictHostKeyChecking", "no");
                createFileSession.connect();

                // 채널 생성
                Channel channel = createFileSession.openChannel("exec");
                ((ChannelExec) channel).setCommand("touch /home/" + user.getServerOption() + "/" + rootDirectory + "/" + fileName);
                // 명령어 실행
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                channel.setOutputStream(outputStream);

                channel.connect();
                while (!channel.isClosed()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                    }
                }
                // 채널과 세션 종료
                channel.disconnect();
                createFileSession.disconnect();

                JTree newTree = remakeDirectoryStructure(project, serverIp, name);
                openFileListener(newTree, project, serverIp);

                JScrollPane scrollPane = getScrollPaneByName(name);
                scrollPane.setViewportView(newTree); // JScrollPane의 뷰포트를 새 트리로 설정
                scrollPane.revalidate(); // 레이아웃 재계산
                scrollPane.repaint(); // 패널 다시 그리기

                JOptionPane.showMessageDialog(null, "File creation complete!");
            } catch (JSchException jSchException) {
                jSchException.printStackTrace();
            }
        });

        // 디렉토리 내 파일 노드에 액션 리스너 생성 - 파일 열기
        openFile.addActionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                String newFileName = node.getUserObject().toString();
                System.out.println("파일 열기 선택됨: " + newFileName);

                /*
                FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
                for (VirtualFile virtualFile : fileEditorManager.getOpenFiles()) {
                    fileEditorManager.closeFile(virtualFile);
                }
                */

                int confirmed = JOptionPane.showConfirmDialog(null,
                        "Do you want to open the file with an editor?",
                        "Open Editor",
                        JOptionPane.YES_NO_OPTION);

                if (confirmed == JOptionPane.YES_OPTION) {
                    User user = userInfo.getUserByServerIP(serverIp);
                    try {
                        JSch jsch = new JSch();
                        jsch.addIdentity(user.getPemKeyPath());
                        Session openSession = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
                        openSession.setConfig("StrictHostKeyChecking", "no");
                        openSession.connect();

                        // 백그라운드 스레드에서 파일 다운로드 작업 실행 - UI 응답성 보장
                        ApplicationManager.getApplication().executeOnPooledThread(() -> {
                            // SFTP 방식으로 클릭한 서버 내 파일을 로컬에 저장 후 에디터로 열기
                            MyWebSocketServer server = new MyWebSocketServer(1234, project);
                            server.startServer();
                            CodeSyncFileManager fileManager = new CodeSyncFileManager(project, openSession, server);
                            fileManager.downloadFileAndOpenInEditor(newFileName, user.getName(), user.getServerOption(), user.getServerIP());
                        });

                    } catch (JSchException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        });

        // 디렉토리 내 파일 노드에 액션 리스너 생성 - 파일 이름 수정
        renameFile.addActionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                String originFileName = node.getUserObject().toString();
                System.out.println("파일 수정 선택됨: " + originFileName);

                System.out.println("renameFile call!!!");

                String updateFileName = JOptionPane.showInputDialog("생성할 파일명을 입력하세요:", originFileName);
                if (updateFileName == null || updateFileName.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(null, "파일 이름이 유효하지 않습니다.");
                    return;
                } else if (updateFileName.equals(originFileName.trim())) {
                    JOptionPane.showMessageDialog(null, "파일 이름이 기존과 동일합니다.");
                    return;
                }

                try {
                    User user = userInfo.getUserByServerIP(serverIp);
                    JSch jsch = new JSch();
                    jsch.addIdentity(user.getPemKeyPath());
                    Session createFileSession = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
                    createFileSession.setConfig("StrictHostKeyChecking", "no");
                    createFileSession.connect();

                    // 채널 생성
                    Channel channel = createFileSession.openChannel("exec");
                    ((ChannelExec) channel).setCommand("mv /home/" + user.getServerOption() + "/" + rootDirectory + "/" + originFileName + " "
                            + "/home/" + user.getServerOption() + "/" + rootDirectory + "/" + updateFileName);
                    // 명령어 실행
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    channel.setOutputStream(outputStream);

                    channel.connect();
                    while (!channel.isClosed()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                        }
                    }
                    // 채널과 세션 종료
                    channel.disconnect();
                    createFileSession.disconnect();

                    JTree newTree = remakeDirectoryStructure(project, serverIp, name);
                    openFileListener(newTree, project, serverIp);

                    JScrollPane scrollPane = getScrollPaneByName(name);
                    scrollPane.setViewportView(newTree); // JScrollPane의 뷰포트를 새 트리로 설정
                    scrollPane.revalidate(); // 레이아웃 재계산
                    scrollPane.repaint(); // 패널 다시 그리기

                    JOptionPane.showMessageDialog(null, "File Name Update Complete!");
                } catch (JSchException jSchException) {
                    jSchException.printStackTrace();
                }
            }
        });

        // 디렉토리 내 파일 노드에 액션 리스너 생성 - 파일 삭제
        deleteFile.addActionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                String deleteFileName = node.getUserObject().toString();
                System.out.println("파일 삭제 선택됨: " + deleteFileName);

                System.out.println("deleteFile call!!!");
                int confirmed = JOptionPane.showConfirmDialog(null,
                        "정말 삭제??",
                        "Open Editor",
                        JOptionPane.YES_NO_OPTION);

                if (confirmed == JOptionPane.YES_OPTION) {
                    try {
                        User user = userInfo.getUserByServerIP(serverIp);
                        JSch jsch = new JSch();
                        jsch.addIdentity(user.getPemKeyPath());
                        Session deleteFileSession = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
                        deleteFileSession.setConfig("StrictHostKeyChecking", "no");
                        deleteFileSession.connect();

                        // 채널 생성
                        Channel channel = deleteFileSession.openChannel("exec");
                        ((ChannelExec) channel).setCommand("rm /home/" + user.getServerOption() + "/" + rootDirectory + "/" + deleteFileName);
                        // 명령어 실행
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        channel.setOutputStream(outputStream);

                        channel.connect();
                        while (!channel.isClosed()) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException ignored) {
                            }
                        }
                        // 채널과 세션 종료
                        channel.disconnect();
                        deleteFileSession.disconnect();

                        JTree newTree = remakeDirectoryStructure(project, serverIp, name);
                        openFileListener(newTree, project, serverIp);

                        JScrollPane scrollPane = getScrollPaneByName(name);
                        scrollPane.setViewportView(newTree); // JScrollPane의 뷰포트를 새 트리로 설정
                        scrollPane.revalidate(); // 레이아웃 재계산
                        scrollPane.repaint(); // 패널 다시 그리기

                        JOptionPane.showMessageDialog(null, "File delete complete!");
                    } catch (JSchException jSchException) {
                        jSchException.printStackTrace();
                    }
                }
            }
        });

        // 팝업 메뉴바에 액션 리스너 등록
        popupMenuDirectory.add(refreshDirectory);
        popupMenuDirectory.add(createFile);

        popupMenuFile.add(openFile);
        popupMenuFile.add(renameFile);
        popupMenuFile.add(deleteFile);

        return tree;
    }

    // 디렉토리 구조 Tree에 담기
    public void populateTree(String directoryStructure, DefaultMutableTreeNode rootNode) {
        String[] lines = directoryStructure.split("\n");

        for (String line : lines) {
            line = line.trim();

            if (!line.endsWith("/")) { // 폴더가 아닌, 파일(.md 등)만 보여주기
                DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(line);
                rootNode.add(fileNode);
            }
        }
    }

    public JButton addServerTabListener(Project project, ToolWindow toolWindow) {
        JButton addButton = new JButton("Add Connection");

        addButton.addActionListener(e -> {
            if (tabbedPane.getTabCount() == 3) {
                JOptionPane.showMessageDialog(null, "Only a maximum of 3 connections can be registered!");
                return;
            }

            ConnectionPanel newConnectionPanel = new ConnectionPanel();
            if (tabbedPane.indexOfTab("New") == -1) {
                newConnectionPanel.getConnectButton().addActionListener(ne -> {
                    PanelInfo newPanelInfo = new PanelInfo(newConnectionPanel);
                    onShowing(project, toolWindow, newPanelInfo);

                    // PersistentStateComponent 저장
                    userInfo.addUser(new User(newConnectionPanel));
                    System.out.println(userInfo.getUsers().size());

                    tabbedPane.remove(tabbedPane.indexOfTab("New"));
                });
                newConnectionPanel.getCancelButton().addActionListener(ne -> {
                    if (tabbedPane.getTabCount() != 0) {
                        tabbedPane.remove(tabbedPane.indexOfTab("New"));
                    }
                });
                tabPanel = newConnectionPanel;
                tabbedPane.addTab("New", tabPanel);
                tabbedPane.setSelectedIndex(tabbedPane.indexOfTab("New"));

                mainPanel.revalidate();
                mainPanel.repaint();
            } else {
                tabbedPane.setSelectedIndex(tabbedPane.indexOfTab("New"));
            }
        });

        return addButton;
    }

    public void openFileListener(JTree tree, Project project, String serverIp) {
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) { // 더블 클릭 시
                    User user = userInfo.getUserByServerIP(serverIp);

                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        String fileName = node.getUserObject().toString();

                        int confirmed = JOptionPane.showConfirmDialog(null,
                                "Do you want to open the file with an editor?",
                                "Open Editor",
                                JOptionPane.YES_NO_OPTION);

                        if (confirmed == JOptionPane.YES_OPTION) {
                            /*
                            // 모든 열린 에디터 닫기 - 에디터별 서버 업로드 적용이 불가하기 때문에 다른 에디터는 종료
                            FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
                            for (VirtualFile virtualFile : fileEditorManager.getOpenFiles()) {
                                fileEditorManager.closeFile(virtualFile);
                            }
                            */

                            try {
                                JSch jsch = new JSch();
                                jsch.addIdentity(user.getPemKeyPath());
                                Session session = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
                                session.setConfig("StrictHostKeyChecking", "no");
                                session.connect();

                                // 백그라운드 스레드에서 파일 다운로드 작업 실행 - UI 응답성 보장
                                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                                    // SFTP 방식으로 클릭한 서버 내 파일을 로컬에 저장 후 에디터로 열기
                                    MyWebSocketServer server = new MyWebSocketServer(1234, project);
                                    server.startServer();
                                    CodeSyncFileManager fileManager = new CodeSyncFileManager(project, session, server);
                                    fileManager.downloadFileAndOpenInEditor(fileName, user.getName(), user.getServerOption(), user.getServerIP());
                                });

                            } catch (JSchException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                }
            }
        });
    }

    public JButton renameServerTabListener(Project project, String name) {
        JButton renameButton = new JButton("rename");
        renameButton.addActionListener(e -> {
            String renameServerTabName = JOptionPane.showInputDialog("수정할 탭 이름을 입력하세요:", name);

            int index = tabbedPane.indexOfTab(name);
            if(index != -1) {
                if(tabbedPane.indexOfTab(renameServerTabName.trim()) != -1) {
                    JOptionPane.showMessageDialog(null, "탭 이름이 중복됩니다.");
                }
                else {
                    tabbedPane.setTitleAt(index, renameServerTabName.trim());
                }
            }
            else {
                System.out.println("존재하지 않는 탭");
            }
        });

        // userInfo 캐시값 변경 필요 + 새로고침
        
        return renameButton;
    }

    public JButton deleteServerTabListener(Project project, ToolWindow toolWindow, String name, String serverIp) {
        JButton deleteButton = new JButton("delete");
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int confirmed = JOptionPane.showConfirmDialog(null,
                        "Are you sure you want to delete it?",
                        "Delete Server Tab",
                        JOptionPane.YES_NO_OPTION);

                if (confirmed == JOptionPane.YES_OPTION) {
                    int index = tabbedPane.indexOfTab(name);
                    if (index != -1) {
                        tabbedPane.remove(index);
                        userInfo.removeUserByServerIP(serverIp);

                        if (tabbedPane.getTabCount() == 0) {
                            ConnectionPanel initConnectionPanel = new ConnectionPanel();
                            initConnectionPanel.getConnectButton().addActionListener(ie -> {
                                PanelInfo initPanelInfo = new PanelInfo(initConnectionPanel);
                                onShowing(project, toolWindow, initPanelInfo);

                                // PersistentStateComponent 저장
                                userInfo.addUser(new User(initConnectionPanel));
                                System.out.println(userInfo.getUsers().size());

                                tabbedPane.remove(tabbedPane.indexOfTab("New"));
                            });
                            tabPanel = initConnectionPanel;

                            tabbedPane.addTab("New", tabPanel);
                            mainPanel.removeAll();
                            mainPanel.add(tabbedPane);
                            mainPanel.revalidate();
                            mainPanel.repaint();
                        }
                    }
                }
            }
        });
        return deleteButton;
    }

    public JButton refreshDirectoryStructureListener(JScrollPane scrollPane, Project project, String serverIp, String name) {
        JButton refreshButton = new JButton("refresh");
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 트리와 관련된 컴포넌트 다시 초기화할 수 있음
                JTree tree = remakeDirectoryStructure(project, serverIp, name);
                openFileListener(tree, project, serverIp);
                scrollPane.setViewportView(tree); // JScrollPane의 뷰포트를 새 트리로 설정
                scrollPane.revalidate(); // 레이아웃 재계산
                scrollPane.repaint(); // 패널 다시 그리기

                /*
                FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
                for (VirtualFile virtualFile : fileEditorManager.getOpenFiles()) {
                    fileEditorManager.closeFile(virtualFile);
                }
                */

                JOptionPane.showMessageDialog(null, "Refresh Success!");
            }
        });

        return refreshButton;
    }

    public JScrollPane getScrollPaneByName(String name) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).equals(name)) {
                // 해당 탭의 컴포넌트를 가져옴
                Component tabComponent = tabbedPane.getComponentAt(i);
                if (tabComponent instanceof JPanel) {
                    JPanel tabPanel = (JPanel) tabComponent;
                    for (Component comp : tabPanel.getComponents()) {
                        if (comp instanceof JScrollPane) {
                            return (JScrollPane) comp; // JScrollPane 반환
                        }
                    }
                }
            }
        }
        return null; // 해당 이름의 탭이 없거나 JScrollPane이 없는 경우 null 반환
    }
}
