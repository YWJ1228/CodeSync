package com.ssafy.codesync.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
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
//import com.ssafy.codesync.util.CodeSyncFileManagerOrg;
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
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DirectoryStructureToolWindowFactory implements ToolWindowFactory {
    // mainPanel: CodeSync 좌측 툴바의 메인 패널
    // tabbedPane: 사용자가 추가한 각각의 서버 탭 묶음
    // tabPanel: tabbedPane에 들어가는 각각의 서버 탭
    // mainPanel > tabbedPane > tabPanel
    // userInfo: 플러그인이 종료되어도 저장되는 PersistentStateComponent 데이터 (IP, PEM_KEY_PATH 등 저장)
    // rootDirectory: 연결할 서버의 루트 경로 지정
    private JPanel mainPanel;
    private JBTabbedPane tabbedPane;
    private JPanel tabPanel;
    private UserInfo userInfo = ServiceManager.getService(UserInfo.class);
    private final String rootDirectory = "CodeSync";

    // regist-server: JPanel로 입력 받은 서버 등록 정보를 저장하는 클래스
    public class PanelInfo {
        public String teamName;
        public String serverIP;
        public String pemKeyPath;
        public String serverOption;

        public PanelInfo() {}
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

    // 플러그인이 최초 실행이 좌측에 툴 윈도우를 생성하는 메서드
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // PersistentStateComponent 테스트 삭제용 메서드 - 플러그인 실행마다 기존에 저장했던 팀 등록 정보 초기화
        // userInfo.removeAll();

        mainPanel = new JPanel(new BorderLayout());

        JPanel InitPanel = new JPanel(new GridBagLayout());
        JButton InitButton = new JButton("Check Settings");
        InitButton.addActionListener(e -> {
            mainPanel.remove(InitButton);
            mainPanel.remove(InitPanel);
            JLabel loadingLabel = new JLabel("Now loading...", SwingConstants.CENTER);
            mainPanel.add(loadingLabel, BorderLayout.CENTER);

            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                initializePluginResources();

                tabbedPane = new JBTabbedPane();
                ConnectionPanel connectionPanel = new ConnectionPanel();

                // 기존에 등록했던 팀 정보가 있는지 검사(if-else)
                if (userInfo.getUsers().isEmpty()) {
                    System.out.println("Not Exist UserInfo!!!");
                    connectionPanel.getConnectButton().addActionListener(e1 -> {
                        // 불러올 팀 정보 클래스에 담기
                        PanelInfo panelInfo = new PanelInfo(connectionPanel);

                        // 등록 팀 디렉토리 구조 및 버튼과 팝업 메뉴 리스너 생성
                        if (onShowing(project, toolWindow, panelInfo)) {
                            // PersistentStateComponent 저장
                            userInfo.addUser(new User(connectionPanel));

                            // 팀 등록 정보 작성한 탭 삭제
                            tabbedPane.remove(tabbedPane.indexOfTab("New"));
                        }
                    });
                    // 팀 등록 정보 작성할 탭 생성
                    tabPanel = connectionPanel;
                    tabbedPane.addTab("New", tabPanel);
                    mainPanel.add(tabbedPane);
                } else {
                    System.out.println("Exist UserInfo!!!");

                    for (int i = 0; i < userInfo.getUsers().size(); i++) {
                        System.out.println("Load Team's IP: " + userInfo.getUsers().get(i).getServerIP());

                        // 팀 정보 불러오기
                        User user = userInfo.getUsers().get(i);
                        PanelInfo panelInfo = new PanelInfo(user);

                        // 각 팀별 디렉토리 구조 및 버튼과 팝업 메뉴 리스너가 등록된 탭 생성
                        if (!onShowing(project, toolWindow, panelInfo)) {
                            // 저장되어 있는 정보 중, 잘못된 정보는 삭제
                            userInfo.removeUser(user);
                        }
                    }
                    if (tabbedPane.getTabCount() == 0) {
                        connectionPanel.getConnectButton().addActionListener(e2 -> {
                            // 불러올 팀 정보 클래스에 담기
                            PanelInfo panelInfo = new PanelInfo(connectionPanel);

                            // 등록 팀 디렉토리 구조 및 버튼과 팝업 메뉴 리스너 생성
                            if (onShowing(project, toolWindow, panelInfo)) {
                                // PersistentStateComponent 저장
                                userInfo.addUser(new User(connectionPanel));

                                // 팀 등록 정보 작성한 탭 삭제
                                tabbedPane.remove(tabbedPane.indexOfTab("New"));
                            }
                        });
                        // 팀 등록 정보 작성할 탭 생성
                        tabPanel = connectionPanel;
                        tabbedPane.addTab("New", tabPanel);
                        mainPanel.add(tabbedPane);
                    }
                }

                mainPanel.remove(loadingLabel);
                toolWindow.getComponent().add(mainPanel);
                JOptionPane.showMessageDialog(null, "Check Success!");
            });
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; // x 위치
        gbc.gridy = 0; // y 위치
        gbc.weightx = 1; // 가중치 설정
        gbc.weighty = 1; // 가중치 설정
        gbc.anchor = GridBagConstraints.CENTER; // 중앙 정렬
        gbc.fill = GridBagConstraints.NONE;
        InitPanel.setPreferredSize(new Dimension(150, 50));
        InitPanel.add(InitButton, gbc); // 버튼을 buttonPanel에 추가
        mainPanel.add(InitPanel, BorderLayout.CENTER); // buttonPanel을 mainPanel의 CENTER에 추가

        toolWindow.getComponent().add(mainPanel, BorderLayout.CENTER); // mainPanel을 toolWindow의 CENTER에 추가
    }

    // 팀 탭 생성 - 서버 연결, 디렉토리 구조 불러오기, 리스너 등록(버튼, 마우스 클릭 이벤트, 우클릭 팝업 메뉴 등)
    public boolean onShowing(Project project, ToolWindow toolWindow, PanelInfo panelInfo) {
        String teamName = panelInfo.teamName;
        String serverIP = panelInfo.serverIP.trim();
        String pemKeyPath = panelInfo.pemKeyPath.trim();
        String serverOption = panelInfo.serverOption;

        // 서버 탭 이름 중복 검사
        if (tabbedPane.indexOfTab(teamName) != -1) {
            JOptionPane.showMessageDialog(null, "A duplicate name exists!");
            return false;
        }

        try {
            tabPanel = new JPanel(new BorderLayout());
            tabPanel.setLayout(new BoxLayout(tabPanel, BoxLayout.Y_AXIS));

            JSch jsch = new JSch();
            jsch.addIdentity(pemKeyPath);

            Session session = jsch.getSession(serverOption, serverIP, 22);
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect();

            // CodeSync 디렉토리 존재 여부 확인 후, 없으면 생성
            if(!checkRootDirectory(session)) {
                JOptionPane.showMessageDialog(null, "Root Directory Create Error!");
                return false;
            }

            // 세션을 통해 연결된 서버에서 파일 디렉토리 구조 생성
            JTree tree = makeDirectoryStructure(project, session, serverOption, serverIP, teamName);

            session.disconnect();

            // 생성한 파일 디렉토리 구조를 탭 패널에 추가
            JScrollPane scrollPane = new JBScrollPane(tree);
            tabPanel.add(scrollPane);

            // 리스너 추가 - 디렉토리 구조의 파일명 더블 클릭 시 파일 열기
            openFileListener(tree, project, serverIP);

            // 서버 탭 이름 수정, 서버 디렉토리 불러오기 새로고침, 서버 탭 삭제 버튼을 담기 위한 패널 생성
            JPanel buttonPanel = new JPanel(new FlowLayout());
            // 리스너 추가 - 서버 탭 이름 수정 버튼
            JButton renameTabNameButtonPanel = renameServerTabListener(project, serverIP);
            buttonPanel.add(renameTabNameButtonPanel);
            // 리스너 추가 - 서버 디렉토리 불러오기 새로고침 버튼
            JButton refreshButtonPanel = refreshDirectoryStructureListener(scrollPane, project, serverIP, teamName);
            buttonPanel.add(refreshButtonPanel);
            // 리스너 추가 - 서버 탭 삭제 버튼
            JButton deleteButtonPanel = deleteServerTabListener(project, toolWindow, teamName, serverIP);
            buttonPanel.add(deleteButtonPanel);
            tabPanel.add(buttonPanel);

            // 디렉토리 구조와 리스너(파일 열기, 버튼)을 추가한 탭을 탭 묶음 패널에 추가
            tabbedPane.addTab(teamName, tabPanel);
            mainPanel.add(tabbedPane);

            // 리스너 추가 - 서버 탭 추가 버튼
            JButton addButton = addServerTabListener(project, toolWindow);
            mainPanel.add(addButton, BorderLayout.SOUTH);

            // 반영된 패널을 리로드 하기 위해 새로고침
            mainPanel.revalidate();
            mainPanel.repaint();

            return true;

        }
        catch (Exception exception) {
            JOptionPane.showMessageDialog(null, "Server Connection Error!");
            exception.printStackTrace();
            return false;
        }
    }

    /* 아래의 메서드부터는 모두 onShowing 메서드 내부에서 동작하는 메서드들 */

    // 서버 디렉토리 구조를 Tree형식으로 가져오기 위한 메서드
    // 진입 순서: <<makeDirectoryStructure>> -> getDirectoryStructure -> return(createFileTree)
    public JTree makeDirectoryStructure(Project project, Session session, String serverOption, String serverIp, String name) {
        try {
            // 커널 명령어(ls)를 통해 서버의 파일 구조를 가져옴
            String directoryStructure = getDirectoryStructure(session, "/home/" + serverOption + "/" + rootDirectory);
            return createFileTree(directoryStructure, project, serverIp, name);
        } catch (JSchException jSchException) {
            jSchException.printStackTrace();
        }
        return null;
    }

    // 서버 디렉토리 구조를 Tree형식으로 가져오기 위한 메서드 - 새로고침 버튼 클릭
    // 진입 순서: <<remakeDirectoryStructure>> -> getDirectoryStructure -> return(createFileTree)
    public JTree remakeDirectoryStructure(Project project, String serverIp, String teamName) {
        try {
            User user = userInfo.getUserByServerIP(serverIp);
            JSch jsch = new JSch();
            jsch.addIdentity(user.getPemKeyPath());
            Session session = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
            session.setConfig("StrictHostKeyChecking", "no");

            session.connect();
            String directoryStructure = getDirectoryStructure(session, "/home/" + user.getServerOption() + "/" + rootDirectory);
            session.disconnect();

            return createFileTree(directoryStructure, project, serverIp, teamName);
        } catch (JSchException jSchException) {
            jSchException.printStackTrace();
        }
        return null;
    }

    // 팀 서버에 "ls -p" 명령어를 통한 출력 결과를 불러오는 메서드
    // 진입 순서: makeDirectoryStructure or remakeDirectoryStructure -> <<getDirectoryStructure>> -> return(createFileTree)
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

    // getDirectoryStructure 메서드를 통해 불러온 ls 출력 결과를 파싱하여 Tree 구조를 생성 + 리스너 등록 메서드
    public Tree createFileTree(String directoryStructure, Project project, String serverIp, String teamName) {
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(serverIp + "'s CodeSync Directory");

        String[] lines = directoryStructure.split("\n");

        for (String line : lines) {
            line = line.trim();

            if (!line.endsWith("/")) { // 폴더가 아닌, 파일(.md 등)만 보여주기
                DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(line);
                rootNode.add(fileNode);
            }
        }

        // Tree 구조 생성
        Tree tree = new Tree(rootNode);

        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // 팝업 메뉴 설정 - 디렉토리 새로고침, 파일 생성, 파일 열기, 파일 이름 변경, 파일 삭제
        JPopupMenu directoryPopupMenu = new JPopupMenu();
        JMenuItem refreshDirectoryItem = new JMenuItem("Refresh Directory");
        JMenuItem createFileItem = new JMenuItem("Create File");

        JPopupMenu filePopupMenu = new JPopupMenu();
        JMenuItem openFileItem = new JMenuItem("Open File");
        JMenuItem renameFileItem = new JMenuItem("Rename File");
        JMenuItem deleteFileItem = new JMenuItem("Delete File");

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
                if (selRow == 0) {
                    directoryPopupMenu.show(e.getComponent(), e.getX(), e.getY());
                } else if (selPath != null) {
                    tree.setSelectionPath(selPath);
                    filePopupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        // 디렉토리 노드에 액션 리스너 생성 - 파일 새로고침
        refreshDirectoryItem.addActionListener(e -> {
            JTree newTree = remakeDirectoryStructure(project, serverIp, teamName);
            openFileListener(newTree, project, serverIp);

            JScrollPane scrollPane = getScrollPaneByTeamName(teamName);
            // JScrollPane에 새로 불러온 디렉토리 트리 구조를 설정
            scrollPane.setViewportView(newTree);
            // 새로고침
            scrollPane.revalidate();
            scrollPane.repaint();

            JOptionPane.showMessageDialog(null, "Refresh Success!");
        });

        // 디렉토리 노드에 액션 리스너 생성 - 파일 생성
        createFileItem.addActionListener(e -> {
            String pattern = "^[^<>:\"/|?*]*\\.(md|js|ts|java|txt)$";
            String fileName = JOptionPane.showInputDialog("Enter the file name to be created");
            fileName = fileName.trim();
            if (fileName.isEmpty()) {
                JOptionPane.showMessageDialog(null, "The file name is empty!");
                return;
            }
            else if(!Pattern.matches(pattern, fileName)) {
                JOptionPane.showMessageDialog(null, "The file name is incorrect!");
                return;
            }
            else if(!(fileName.endsWith(".md") || fileName.endsWith(".js") || fileName.endsWith(".ts") || fileName.endsWith(".java") || fileName.endsWith(".txt"))) {
                JOptionPane.showMessageDialog(null, "Currently supported extensions are: (.md/.js/.ts/.java/.txt)");
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

                JTree newTree = remakeDirectoryStructure(project, serverIp, teamName);
                openFileListener(newTree, project, serverIp);

                JScrollPane scrollPane = getScrollPaneByTeamName(teamName);
                scrollPane.setViewportView(newTree); // JScrollPane의 뷰포트를 새 트리로 설정
                scrollPane.revalidate(); // 레이아웃 재계산
                scrollPane.repaint(); // 패널 다시 그리기

                JOptionPane.showMessageDialog(null, "File creation complete!");
            } catch (JSchException jSchException) {
                jSchException.printStackTrace();
            }
        });

        // 디렉토리 내 파일 노드에 액션 리스너 생성 - 파일 열기
        openFileItem.addActionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                String newFileName = node.getUserObject().toString();

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

                            // ** plugin 설치 시 해당 부분 사용
                            String scriptPath = System.getProperty("java.io.tmpdir") + "CodeSync";
                            System.out.println("init : "+scriptPath);
                            CodeSyncFileManager fileManager = new CodeSyncFileManager(project, server, scriptPath);
                            // CodeSyncFileManager fileManager = new CodeSyncFileManager(project, openSession, server);
                            fileManager.downloadFileAndOpenInEditor(newFileName,user.getServerOption(),user.getServerIP());

//                            CodeSyncFileManagerOrg fileManager = new CodeSyncFileManagerOrg(project, openSession, server, scriptPath);
//                            // CodeSyncFileManager fileManager = new CodeSyncFileManager(project, session, server);
//                            // SFTP 방식으로 클릭한 서버 내 파일을 로컬에 저장 후 에디터로 열기
//                            fileManager.downloadFileAndOpenInEditor(newFileName, user.getName(), user.getServerOption(), user.getServerIP());

                        });

                    } catch (JSchException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        });

        // 디렉토리 내 파일 노드에 액션 리스너 생성 - 파일 이름 수정
        renameFileItem.addActionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                String originFileName = node.getUserObject().toString();

                String updateFileName = JOptionPane.showInputDialog("Enter the file name to change", originFileName);
                if (updateFileName == null || updateFileName.trim().isEmpty()) {
                    JOptionPane.showMessageDialog(null, "The file name is incorrect!");
                    return;
                } else if (updateFileName.equals(originFileName.trim())) {
                    JOptionPane.showMessageDialog(null, "The file name is the same as before.");
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

                    JTree newTree = remakeDirectoryStructure(project, serverIp, teamName);
                    openFileListener(newTree, project, serverIp);

                    JScrollPane scrollPane = getScrollPaneByTeamName(teamName);
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
        deleteFileItem.addActionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path != null) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                String deleteFileName = node.getUserObject().toString();

                int confirmed = JOptionPane.showConfirmDialog(null,
                        "Are you sure you want to delete the file? : " + deleteFileName,
                        "DELETE FILE",
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

                        JTree newTree = remakeDirectoryStructure(project, serverIp, teamName);
                        openFileListener(newTree, project, serverIp);

                        JScrollPane scrollPane = getScrollPaneByTeamName(teamName);
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
        directoryPopupMenu.add(refreshDirectoryItem);
        directoryPopupMenu.add(createFileItem);

        filePopupMenu.add(openFileItem);
        filePopupMenu.add(renameFileItem);
        filePopupMenu.add(deleteFileItem);

        return tree;
    }

    // 리스너 추가 메서드 - 디렉토리 구조의 파일명 더블 클릭 시 파일 열기
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
                            try {
                                JSch jsch = new JSch();
                                jsch.addIdentity(user.getPemKeyPath());
                                Session session = jsch.getSession(user.getServerOption(), user.getServerIP(), 22);
                                session.setConfig("StrictHostKeyChecking", "no");

                                session.connect(); // session.disconect()는 백그라운드 스레드 코드 내부로 이동

                                // 백그라운드 스레드에서 파일 다운로드 작업 실행 - UI 응답성 보장
                                ApplicationManager.getApplication().executeOnPooledThread(() -> {
                                    MyWebSocketServer server = new MyWebSocketServer(1234, project);
                                    server.startServer();

                                    // ** plugin 설치 시 해당 부분 사용
                                    String scriptPath = System.getProperty("java.io.tmpdir") + "CodeSync";
                                    System.out.println("init : "+scriptPath);

                                    CodeSyncFileManager fileManager = new CodeSyncFileManager(project, server, scriptPath);
                                    // CodeSyncFileManager fileManager = new CodeSyncFileManager(project, session, server);
                                    // SFTP 방식으로 클릭한 서버 내 파일을 로컬에 저장 후 에디터로 열기
                                    fileManager.downloadFileAndOpenInEditor(fileName, user.getServerOption(), user.getServerIP());

//                                    CodeSyncFileManagerOrg fileManager = new CodeSyncFileManagerOrg(project, session, server, scriptPath);
//                                    // CodeSyncFileManager fileManager = new CodeSyncFileManager(project, session, server);
//                                    // SFTP 방식으로 클릭한 서버 내 파일을 로컬에 저장 후 에디터로 열기
//                                    fileManager.downloadFileAndOpenInEditor(fileName, user.getName(), user.getServerOption(), user.getServerIP());
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

    // 리스너 추가 메서드 - 서버 탭 추가 버튼
    public JButton addServerTabListener(Project project, ToolWindow toolWindow) {
        JButton addButton = new JButton("+ New Server");

        addButton.addActionListener(e -> {
            if (tabbedPane.getTabCount() == 4) {
                JOptionPane.showMessageDialog(null, "Only a maximum of 3 connections can be registered!");
                return;
            }

            ConnectionPanel newConnectionPanel = new ConnectionPanel();
            if (tabbedPane.indexOfTab("New") == -1) {
                // 새롭게 생성한 팀 정보 입력 탭에 연결 버튼 리스너 등록
                newConnectionPanel.getConnectButton().addActionListener(ne -> {
                    PanelInfo newPanelInfo = new PanelInfo(newConnectionPanel);
                    if(onShowing(project, toolWindow, newPanelInfo)) {
                        // PersistentStateComponent 저장
                        userInfo.addUser(new User(newConnectionPanel));

                        // 입력 정보를 받은 New 탭은 삭제
                        tabbedPane.remove(tabbedPane.indexOfTab("New"));
                    }
                });

                // 새롭게 생성한 팀 정보 입력 탭에 취소 버튼 리스너 등록
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

    // 리스너 추가 메서드 - 서버 탭 이름 수정 버튼
    public JButton renameServerTabListener(Project project, String serverIp) {
        JButton renameButton = new JButton("rename");

        renameButton.addActionListener(e -> {
            User user = userInfo.getUserByServerIP(serverIp);
            String originName = user.getName();

            String renameServerTabName = JOptionPane.showInputDialog("Enter the tab name to change!", originName);

            int index = tabbedPane.indexOfTab(originName);
            if(index != -1) {
                if(tabbedPane.indexOfTab(renameServerTabName.trim()) != -1) {
                    JOptionPane.showMessageDialog(null, "A duplicate tab name exists!");
                }
                else {
                    tabbedPane.setTitleAt(index, renameServerTabName.trim());
                    user.setName(renameServerTabName.trim());
                    JOptionPane.showMessageDialog(null, "Tab name change complete!");

                    mainPanel.revalidate();
                    mainPanel.repaint();
                }
            }
            else {
                System.out.println("Tab name change error");
            }
        });

        return renameButton;
    }

    // 리스너 추가 메서드 - 서버 탭 삭제 버튼
    public JButton deleteServerTabListener(Project project, ToolWindow toolWindow, String teamName, String serverIp) {
        JButton deleteButton = new JButton("delete");

        deleteButton.addActionListener(e -> {
            int confirmed = JOptionPane.showConfirmDialog(null,
                    "Are you sure you want to delete it?",
                    "Delete Server Tab",
                    JOptionPane.YES_NO_OPTION);

            if (confirmed == JOptionPane.YES_OPTION) {
                int index = tabbedPane.indexOfTab(teamName);
                if (index != -1) {
                    tabbedPane.remove(index);
                    userInfo.removeUserByServerIP(serverIp);

                    // 모든 탭이 삭제되었다면 서버 등록이 가능한 New 탭 생성
                    if (tabbedPane.getTabCount() == 0) {
                        ConnectionPanel initConnectionPanel = new ConnectionPanel();
                        initConnectionPanel.getConnectButton().addActionListener(ie -> {
                            PanelInfo initPanelInfo = new PanelInfo(initConnectionPanel);
                            if(onShowing(project, toolWindow, initPanelInfo)) {
                                // PersistentStateComponent 저장
                                userInfo.addUser(new User(initConnectionPanel));

                                // 입력 정보를 받은 New 탭은 삭제
                                tabbedPane.remove(tabbedPane.indexOfTab("New"));
                            }
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
        });
        return deleteButton;
    }

    // 리스너 추가 메서드 - 서버 디렉토리 불러오기 새로고침 버튼
    public JButton refreshDirectoryStructureListener(JScrollPane scrollPane, Project project, String serverIp, String teamName) {
        JButton refreshButton = new JButton("refresh");

        refreshButton.addActionListener(e -> {
            // 파일 디렉토리 구조 새로고침 및 파일 열기용 리스너 추가
            JTree tree = remakeDirectoryStructure(project, serverIp, teamName);
            openFileListener(tree, project, serverIp);

            // JScrollPane에 새로 불러온 디렉토리 트리 구조를 설정
            scrollPane.setViewportView(tree);
            // 새로고침
            scrollPane.revalidate();
            scrollPane.repaint();

            JOptionPane.showMessageDialog(null, "Refresh Success!");
        });

        return refreshButton;
    }

    // 서버 탭 이름을 통해 관련 JScrollPane 찾기 메서드
    public JScrollPane getScrollPaneByTeamName(String TeamName) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTitleAt(i).equals(TeamName)) {
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
        return null;
    }

    // ** plugin 설치 시
    public void initializePluginResources() {
        System.out.println("node modules 확인 중...");

        // 리소스 검색

        String path = PathManager.getPluginsPath();
        System.out.println("path : "+path);

        String jarPath = Paths.get(path, "CodeSync/lib/instrumented-CodeSync-2.0.jar").toString();

        // JAR 파일에서 리소스 복사
        String tmpDir = System.getProperty("java.io.tmpdir") + "CodeSync";
        File pluginDir = new File(tmpDir);
        if (!pluginDir.exists()) {
            pluginDir.mkdir();

            copyResourceFromJar(jarPath, "y-websocket/y_websocket.js", new File(pluginDir, "y_websocket.js"));
            copyResourceFromJar(jarPath, "y-websocket/package.json", new File(pluginDir, "package.json"));
            copyResourceFromJar(jarPath, "y-websocket/package-lock.json", new File(pluginDir, "package-lock.json"));
        } else {
            System.out.println("폴더가 이미 존재");
        }


        // node_modules가 없다면 npm install을 실행
        File nodeModulesDir = new File(pluginDir, "node_modules");
        if (!nodeModulesDir.exists()) {
            try {
                Process npmProcess = new ProcessBuilder("cmd.exe", "/c", "npm install")
                    .directory(pluginDir)
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
            System.out.println("node modules 존재");
        }
    }


    // JAR 파일 내 리소스를 외부 파일로 복사하는 메소드
    private void copyResourceFromJar(String jarFilePath, String resourcePath, File targetFile) {
        try (ZipFile zipFile = new ZipFile(jarFilePath)) {

            ZipEntry entry = zipFile.getEntry(resourcePath);
            if (entry != null) {
                // ZipEntry에서 InputStream을 얻어와서 targetFile로 복사
                try (InputStream inputStream = zipFile.getInputStream(entry);
                     OutputStream outputStream = new FileOutputStream(targetFile)) {

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }
                    System.out.println("복사 완료: " + targetFile.getPath());
                }
            } else {
                System.out.println("리소스를 찾을 수 없습니다: " + resourcePath);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean checkRootDirectory(Session session) {
        try {
            // 명령어를 실행하여 결과를 확인
            String command = "test -d " + rootDirectory + " && echo \"Y\" || echo \"N\"";
            String result = executeCommand(session, command);

            // 결과가 "N"인 경우 CodeSync 폴더 생성
            if ("N".equals(result.trim())) {
                System.out.println(rootDirectory + " 루트 디렉토리 생성");
                String createDirCommand = "mkdir " + rootDirectory;
                executeCommand(session, createDirCommand);
            }
            return true; // 폴더 생성 또는 폴더 존재 확인 완료
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String executeCommand(Session session, String command) throws IOException {
        StringBuilder output = new StringBuilder();
        try {
            // 명령어 실행
            var channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            channel.setInputStream(null);

            // 명령어 실행 결과를 읽기 위한 스트림
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()))) {
                channel.connect();

                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                channel.disconnect();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output.toString().trim();
    }
}
