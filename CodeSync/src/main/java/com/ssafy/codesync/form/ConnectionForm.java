//package com.ssafy.codesync.form;
//
//import com.intellij.openapi.ui.ComboBox;
//
//import javax.swing.*;
//import java.awt.*;
//
//// Add Connection 버튼 클릭 시 활성화되는 입력 폼
//public class ConnectionForm {
//    public JDialog dialog;
//    public JPanel connectionPanel;
//
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
//    public ConnectionForm() {
//        dialog = new JDialog();
//        connectionPanel = new JPanel(new GridLayout(5, 2));
//
//        teamNameLabel = new JLabel("Name:");
//        teamName = new JTextField();
//
//        serverIpLabel = new JLabel("Server IP:");
//        serverIP = new JTextField();
//
//        pemKeyPathLabel = new JLabel("PEM Key Path:");
//        pemKeyPath = new JTextField();
//
//        serverLabel = new JLabel("Server:");
//        String[] serverOptions = {"Linux", "Ubuntu"};
//        serverTypeComboBox = new ComboBox<>(serverOptions);
//
//        connectButton = new JButton("Connect");
//        cancelButton = new JButton("Cancel");
//    }
//
//    public void makeConnectionForm() {
//        this.connectionPanel.add(teamNameLabel);
//        this.connectionPanel.add(teamName);
//        this.connectionPanel.add(serverIpLabel);
//        this.connectionPanel.add(serverIP);
//        this.connectionPanel.add(pemKeyPathLabel);
//        this.connectionPanel.add(pemKeyPath);
//        this.connectionPanel.add(serverLabel);
//        this.connectionPanel.add(serverTypeComboBox);
//        this.connectionPanel.add(connectButton);
//        this.connectionPanel.add(cancelButton);
//
//        this.dialog.setTitle("Connect to Server");
//        this.dialog.add(connectionPanel, BorderLayout.CENTER);
//        this.dialog.pack();
//        this.dialog.setLocation(200, 200);
//        this.dialog.setModal(true);
//    }
//}