//package com.ssafy.codesync.form;
//
//import com.intellij.openapi.ui.ComboBox;
//
//import javax.swing.*;
//import java.awt.*;
//
//public class ConnectionPanel extends JPanel {
//    public JLabel teamNameLabel;
//    public JLabel serverIpLabel;
//    public JLabel pemKeyPathLabel;
//    public JLabel serverLabel;
//
//    public JTextField teamName;
//    public JTextField serverIP;
//    public JTextField pemKeyPath;
//    public ComboBox<String> serverTypeComboBox;
//
//    public JButton connectButton;
//    public JButton cancelButton;
//
//    public ConnectionPanel() {
//        this.setLayout(new GridBagLayout());
//        GridBagConstraints gbc = new GridBagConstraints();
//        gbc.fill = GridBagConstraints.HORIZONTAL;
//        gbc.insets = new Insets(10, 10, 10, 10); // 컴포넌트 간의 여백 설정
//
//        // 팀 이름
//        teamNameLabel = new JLabel("Name:");
//        gbc.gridx = 0;
//        gbc.gridy = 0;
//        this.add(teamNameLabel, gbc);
//
//        teamName = new JTextField(20); // 너비 20으로 설정
//        gbc.gridx = 1;
//        this.add(teamName, gbc);
//
//        // 서버 IP
//        serverIpLabel = new JLabel("Server IP:");
//        gbc.gridx = 0;
//        gbc.gridy = 1;
//        this.add(serverIpLabel, gbc);
//
//        serverIP = new JTextField(20);
//        gbc.gridx = 1;
//        this.add(serverIP, gbc);
//
//        // PEM 키 경로
//        pemKeyPathLabel = new JLabel("PEM Key Path:");
//        gbc.gridx = 0;
//        gbc.gridy = 2;
//        this.add(pemKeyPathLabel, gbc);
//
//        pemKeyPath = new JTextField(20);
//        gbc.gridx = 1;
//        this.add(pemKeyPath, gbc);
//
//        // 서버 타입
//        serverLabel = new JLabel("Server:");
//        gbc.gridx = 0;
//        gbc.gridy = 3;
//        this.add(serverLabel, gbc);
//
//        String[] serverOptions = {"Linux", "Ubuntu"};
//        serverTypeComboBox = new ComboBox<>(serverOptions);
//        gbc.gridx = 1;
//        this.add(serverTypeComboBox, gbc);
//
//        // Connect 버튼
//        connectButton = new JButton("Connect");
//        gbc.gridx = 0;
//        gbc.gridy = 4;
//        this.add(connectButton, gbc);
//
//        // Cancel 버튼
//        cancelButton = new JButton("Cancel");
//        gbc.gridx = 1;
//        this.add(cancelButton, gbc);
//    }
//
//    public JTextField getTeamName() {
//        return teamName;
//    }
//
//    public JTextField getServerIP() {
//        return serverIP;
//    }
//
//    public JTextField getPemKeyPath() {
//        return pemKeyPath;
//    }
//
//    public ComboBox<String> getServerTypeComboBox() {
//        return serverTypeComboBox;
//    }
//
//    public JButton getConnectButton() {
//        return connectButton;
//    }
//
//    public JButton getCancelButton() {
//        return cancelButton;
//    }
//
//    public void setTeamName(JTextField teamName) {
//        this.teamName = teamName;
//    }
//
//    public void setServerIP(JTextField serverIP) {
//        this.serverIP = serverIP;
//    }
//
//    public void setPemKeyPath(JTextField pemKeyPath) {
//        this.pemKeyPath = pemKeyPath;
//    }
//
//    public void setServerTypeComboBox(ComboBox<String> serverTypeComboBox) {
//        this.serverTypeComboBox = serverTypeComboBox;
//    }
//}
package com.ssafy.codesync.form;

import com.intellij.openapi.ui.ComboBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ConnectionPanel extends JPanel {
    public JLabel teamNameLabel;
    public JLabel serverIpLabel;
    public JLabel pemKeyPathLabel;
    public JLabel webSocketPathLabel;
    public JLabel serverLabel;

    public JTextField teamName;
    public JTextField serverIP;
    public JTextField pemKeyPath;
    public JTextField webSocketPath;
    public ComboBox<String> serverTypeComboBox;

    public JButton connectButton;
    public JButton cancelButton;
    public JButton browseButton; // 파일 선택 버튼 추가
    public JButton browseSocketButton;

    public ConnectionPanel() {
        this.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10); // 컴포넌트 간의 여백 설정

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
        this.add(browseButton, gbc);

        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 파일 선택 대화 상자 열기
                JFileChooser fileChooser = new JFileChooser();
                int returnValue = fileChooser.showOpenDialog(ConnectionPanel.this);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    // 선택한 파일 경로를 JTextField에 설정
                    pemKeyPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
                }
            }
        });

        webSocketPathLabel = new JLabel("Web Socket Path:");
        gbc.gridx = 0;
        gbc.gridy = 3;
        this.add(webSocketPathLabel, gbc);

        webSocketPath = new JTextField(20);
        gbc.gridx = 1;
        this.add(webSocketPath, gbc);

        browseSocketButton = new JButton("Browse");
        gbc.gridx = 2;
        this.add(browseSocketButton, gbc);

        browseSocketButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 파일 선택 대화 상자 열기
                JFileChooser fileChooser = new JFileChooser();
                int returnValue = fileChooser.showOpenDialog(ConnectionPanel.this);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    // 선택한 파일 경로를 JTextField에 설정
                    webSocketPath.setText(fileChooser.getSelectedFile().getAbsolutePath());
                }
            }
        });

        // 서버 타입
        serverLabel = new JLabel("Server:");
        gbc.gridx = 0;
        gbc.gridy = 4;
        this.add(serverLabel, gbc);

        String[] serverOptions = {"Linux", "Ubuntu"};
        serverTypeComboBox = new ComboBox<>(serverOptions);
        gbc.gridx = 1;
        this.add(serverTypeComboBox, gbc);

        // Connect 버튼
        connectButton = new JButton("Connect");
        gbc.gridx = 0;
        gbc.gridy = 5;
        this.add(connectButton, gbc);

        // Cancel 버튼
        cancelButton = new JButton("Cancel");
        gbc.gridx = 1;
        this.add(cancelButton, gbc);
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

    public JTextField getWebSocketPath() {
        return webSocketPath;
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

    public void setWebSocketPath(JTextField websocketPath) {
        this.webSocketPath = websocketPath;
    }

    public void setServerTypeComboBox(ComboBox<String> serverTypeComboBox) {
        this.serverTypeComboBox = serverTypeComboBox;
    }
}
