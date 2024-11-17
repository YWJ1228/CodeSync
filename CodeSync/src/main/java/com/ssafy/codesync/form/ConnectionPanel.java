package com.ssafy.codesync.form;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ConnectionPanel extends JPanel {
    public JLabel teamNameLabel;
    public JLabel serverIpLabel;
    public JLabel pemKeyPathLabel;
    public JLabel serverLabel;

    public JTextField teamName;
    public JTextField serverIP;
    public JTextField pemKeyPath;
    public ComboBox<String> serverTypeComboBox;

    public JButton connectButton;
    public JButton cancelButton;
    public JButton browseButton; // 파일 선택 버튼 추가

    public ConnectionPanel() {
        this.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(10, 5); // 컴포넌트 간의 여백 설정

        // 팀 이름
        teamNameLabel = new JLabel("Name:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        this.add(teamNameLabel, gbc);

        teamName = new JTextField(20); // 너비 20으로 설정
        gbc.gridx = 1;
        this.add(teamName, gbc);

        // 서버 IP
        serverIpLabel = new JLabel("Server IP:");
        gbc.gridx = 0;
        gbc.gridy = 1;
        this.add(serverIpLabel, gbc);

        serverIP = new JTextField(20);
        gbc.gridx = 1;
        this.add(serverIP, gbc);

        // PEM 키 경로
        pemKeyPathLabel = new JLabel("PEM Key Path:");
        gbc.gridx = 0;
        gbc.gridy = 2;
        this.add(pemKeyPathLabel, gbc);

        pemKeyPath = new JTextField(20);
        gbc.gridx = 1;
        this.add(pemKeyPath, gbc);

        // 파일 선택 버튼 추가
        browseButton = new JButton("Browse");
        gbc.gridx = 2; // 2번째 열에 배치
        gbc.weightx = 1.0; // 가용 공간을 균등하게 분배
        this.add(browseButton, gbc);

        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
//                // 파일 선택 대화 상자 열기
//                JFileChooser fileChooser = new JFileChooser();
//                int returnValue = fileChooser.showOpenDialog(ConnectionPanel.this);
//                if (returnValue == JFileChooser.APPROVE_OPTION) {
//                    // 선택한 파일 경로를 JTextField에 설정
//                    pemKeyPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
//                }
                // FileDialog를 사용하여 파일 선택 대화 상자 열기
                FileDialog fileDialog = new FileDialog((Frame) null, "Select a File", FileDialog.LOAD);
                fileDialog.setVisible(true);
                String file = fileDialog.getDirectory() + fileDialog.getFile();
                pemKeyPath.setText(file);
            }
        });

        // 서버 타입
        serverLabel = new JLabel("Server:");
        gbc.gridx = 0;
        gbc.gridy = 3;
        this.add(serverLabel, gbc);

        String[] serverOptions = {"Linux", "Ubuntu"};
        serverTypeComboBox = new ComboBox<>(serverOptions);
        gbc.gridx = 1;
        this.add(serverTypeComboBox, gbc);

        // Connect 버튼
        connectButton = new JButton("Connect");
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 1.0; // 가용 공간을 균등하게 분배
        this.add(connectButton, gbc);

        // Cancel 버튼
        cancelButton = new JButton("Cancel");
        gbc.gridx = 1;
        this.add(cancelButton, gbc);

        // 버튼 크기 고정
        Dimension buttonSize = new Dimension(100, 30); // 원하는 크기로 설정
        connectButton.setPreferredSize(buttonSize);
        cancelButton.setPreferredSize(buttonSize);
        browseButton.setPreferredSize(buttonSize);
    }

    public JTextField getTeamName() {
        return teamName;
    }

    public JTextField getServerIP() {
        return serverIP;
    }

    public JTextField getPemKeyPath() {
        return pemKeyPath;
    }

    public ComboBox<String> getServerTypeComboBox() {
        return serverTypeComboBox;
    }

    public JButton getConnectButton() {
        return connectButton;
    }

    public JButton getCancelButton() {
        return cancelButton;
    }

    public void setTeamName(JTextField teamName) {
        this.teamName = teamName;
    }

    public void setServerIP(JTextField serverIP) {
        this.serverIP = serverIP;
    }

    public void setPemKeyPath(JTextField pemKeyPath) {
        this.pemKeyPath = pemKeyPath;
    }

    public void setServerTypeComboBox(ComboBox<String> serverTypeComboBox) {
        this.serverTypeComboBox = serverTypeComboBox;
    }
}
