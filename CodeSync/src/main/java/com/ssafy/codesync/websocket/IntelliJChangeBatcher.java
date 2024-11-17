package com.ssafy.codesync.websocket;

import com.google.gson.Gson;
import com.intellij.openapi.editor.event.DocumentEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
public class IntelliJChangeBatcher {
    private final MyWebSocketServer server;
    private final List<ChangeEvent> changeBuffer;
    private Timer timer;
    private final long BATCH_INTERVAL_MS = 1; // 배치 전송 간격 (50ms)
    public boolean isBatchingActive;

    public IntelliJChangeBatcher(MyWebSocketServer server) {
        this.server = server;
        this.changeBuffer = new ArrayList<>();
        this.isBatchingActive = false; // 초기에는 배치 비활성화 상태로 설정
        startBatching();
    }

    public synchronized void startBatching() {
        if (isBatchingActive) {
            System.out.println("[ intellij ] Batching is already active.");
            return; // 배치가 이미 활성 상태라면 새로운 타이머 생성 방지
        }
        isBatchingActive = true;
        timer = new Timer(true);

        // 배치 전송 타이머 설정
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (isBatchingActive) { // 배치가 활성화된 경우에만 전송
                    sendBatchChanges();
                }
            }
        }, BATCH_INTERVAL_MS, BATCH_INTERVAL_MS);
    }

    public void dispose() {
        if (timer != null) {
            timer.cancel();
            timer = null; // 참조 해제
        }
        changeBuffer.clear();
        System.out.println("[ intellij ] IntelliJChangeBatcher has been disposed.");
    }

    public void onDocumentChange(DocumentEvent event) {
        System.out.println("[ intellij ] event from onDocumentChange: " + event.getNewFragment().toString());
        int startOffset = event.getOffset();
        int endOffset = event.getOffset() + event.getOldLength();
        String newText = event.getNewFragment().toString();

        // 변경 사항을 버퍼에 추가
        synchronized (changeBuffer) {
            changeBuffer.add(new ChangeEvent(startOffset, endOffset, newText));
        }
    }

    private void sendBatchChanges() {
        List<ChangeEvent> changesToSend;

        synchronized (changeBuffer) {
            if (changeBuffer.isEmpty()) return;

            // 현재 버퍼 내용을 복사하고 초기화
            changesToSend = new ArrayList<>(changeBuffer);
            changeBuffer.clear();
        }

        // 여러 변경 사항을 하나의 메세지로 변환하여 전송
        Gson gson = new Gson();
        String jsonMessage = gson.toJson(changesToSend);
        server.broadcast(jsonMessage);
        System.out.println("sended!");
    }

    private static class ChangeEvent {
        int startOffset;
        int endOffset;
        String newText;

        ChangeEvent(int startOffset, int endOffset, String newText) {
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.newText = newText;
        }
    }
}
