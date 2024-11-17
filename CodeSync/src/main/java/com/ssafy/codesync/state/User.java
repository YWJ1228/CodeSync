package com.ssafy.codesync.state;

import com.ssafy.codesync.form.ConnectionPanel;

public class User {
    private String name;
    private String serverIP;
    private String pemKeyPath;
    private String serverOption;

    public User() {
    }

    public User(String name, String serverIP, String pemKeyPath, String serverOption) {
        this.name = name;
        this.serverIP = serverIP;
        this.pemKeyPath = pemKeyPath;
        this.serverOption = serverOption;
    }
    public User(ConnectionPanel connectionPanel) {
        this.name = connectionPanel.getTeamName().getText();
        this.serverIP = connectionPanel.getServerIP().getText().trim();
        this.pemKeyPath = connectionPanel.getPemKeyPath().getText().trim();
        this.serverOption = connectionPanel.getServerTypeComboBox().getItem().equals("Linux") ? "ec2-user" : "ubuntu";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getServerIP() {
        return serverIP;
    }

    public void setServerIP(String serverIP) {
        this.serverIP = serverIP;
    }

    public String getPemKeyPath() {
        return pemKeyPath;
    }

    public void setPemKeyPath(String pemKeyPath) {
        this.pemKeyPath = pemKeyPath;
    }

    public String getServerOption() {
        return serverOption;
    }

    public void setServerOption(String serverOption) {
        this.serverOption = serverOption;
    }
}
