package com.ssafy.codesync.util;

import com.jcraft.jsch.SftpProgressMonitor;
import javax.swing.*;
import com.intellij.openapi.progress.ProgressIndicator;

public class FileTransferProgressMonitor implements SftpProgressMonitor {
    private final ProgressIndicator indicator;
    private final JLabel progressLabel;
    private final JFrame progressFrame;
    private final long fileSize;
    private long transferredBytes = 0;
    private final String transferType;

    public FileTransferProgressMonitor(String type, ProgressIndicator indicator, long fileSize) {
        this.indicator = indicator;
        System.out.println(indicator);
        this.fileSize = fileSize;
        this.transferType = type;
        this.progressLabel = new JLabel("Starting " + transferType + "...");
        this.progressFrame = createProgressFrame();
    }

    public void showProgressFrame() {
        SwingUtilities.invokeLater(() -> progressFrame.setVisible(true));
    }

    public void closeProgressFrame() {
        SwingUtilities.invokeLater(progressFrame::dispose);
    }

    private JFrame createProgressFrame() {
        JFrame frame = new JFrame(transferType + " Progress");
        frame.setSize(300, 100);
        frame.setLocationRelativeTo(null);
        JPanel panel = new JPanel();
        panel.add(progressLabel);
        frame.add(panel);
        return frame;
    }

    @Override
    public void init(int op, String src, String dest, long max) {
        transferredBytes = 0; // 초기화
        SwingUtilities.invokeLater(() -> progressLabel.setText("Initializing transfer..."));
    }

    @Override
    public boolean count(long count) {
        transferredBytes += count;
        double fraction = (double) transferredBytes / fileSize;
        // 상태바와 알림창 진행률 업데이트
        indicator.setFraction(fraction);
        indicator.setText("Progress: " + (int) (fraction * 100) + "% completed");
        SwingUtilities.invokeLater(() -> progressLabel.setText("Progress: " + (int) (fraction * 100) + "% completed"));
        return true;
    }

    @Override
    public void end() {
        // 진행률 창 닫기
        SwingUtilities.invokeLater(this::closeProgressFrame);

        // 지연 후 성공 메시지 표시를 위해 invokeLater와 Timer 결합
        SwingUtilities.invokeLater(() -> {
            new javax.swing.Timer(10, e -> {
                JOptionPane.showMessageDialog(null, transferType + " Success!");
            }) {{
                setRepeats(false);  // 단일 실행 설정
                start();  // Timer 시작
            }};
        });
    }
}