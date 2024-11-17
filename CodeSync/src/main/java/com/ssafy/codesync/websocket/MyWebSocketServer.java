package com.ssafy.codesync.websocket;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;

import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

public class MyWebSocketServer extends WebSocketServer {

	private Document document; // IntelliJ의 Document 객체
	private Project project;  // IntelliJ Project 객체
	private FileEditorManager fileEditorManager;
	private File tempFile;

	public boolean socketFlag = false;

	// 생성자, 포트 설정
	public MyWebSocketServer(int port, Project project) {
		super(new InetSocketAddress(port));  // WebSocket 서버를 특정 포트에서 리스닝
		this.project = project;
	}

	public void init(Document document, FileEditorManager fileEditorManager, File tempFile) {
		this.document = document;
		this.fileEditorManager = fileEditorManager;
		this.tempFile = tempFile;
	}


	// 클라이언트가 연결되었을 때 호출되는 메서드
	@Override
	public void onOpen(WebSocket conn, ClientHandshake handshake) {
		System.out.println("새로운 클라이언트가 연결되었습니다: " + conn.getRemoteSocketAddress());
		// 연결된 클라이언트에게 메시지 전송
		// conn.send("Welcome to the WebSocket Server!");
	}

	// 클라이언트로부터 메시지를 받았을 때 호출되는 메서드
	@Override
	public void onMessage(WebSocket conn, String message) {
		System.out.println("[ intellij ] 받은 메시지: " + message);
		// 서버로부터 메시지를 받았을 때 처리
		String newMessage = message.replace("\r\n", "\n");
		WriteCommandAction.runWriteCommandAction(project, () -> {
			socketFlag = true;
			document.setText(newMessage);
			socketFlag = false;
			System.out.println("[ intellij(onMessage)] 변경된 내용 수신 : " + newMessage);
//
//			try (FileWriter writer = new FileWriter(tempFile)) {
//				writer.write(newMessage);  // 내용을 덮어씌움
//				System.out.println("[ intellij ] new Message: " + newMessage);
//				System.out.println("[ intellij ] 덮어씌우기 완료");
//			} catch (IOException e) {
//				System.out.println("[ intellij ] "+e.getMessage());
//			}
//
//			 VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(tempFile);
//			 virtualFile.refresh(false, false);
//						fileEditorManager.openFile(virtualFile, true);
//			 			openEditorForFileType(virtualFile);
		});
//		try {
//			JsonObject jsonMessage = JsonParser.parseString(message).getAsJsonObject();
//			int startOffset = jsonMessage.get("startOffset").getAsInt();
//			int endOffset = jsonMessage.get("endOffset").getAsInt();
//			String newText = jsonMessage.get("newText").getAsString();
//
//			// Document에 변경 사항 적용
//			WriteCommandAction.runWriteCommandAction(project, () -> {
//				if (document != null) {
//					document.replaceString(startOffset, endOffset, newText);
//					System.out.println("[ intellij (onMessage) ] Document updated with new text: " + newText);
//				} else {
//					System.err.println("[ intellij (onMessage) ] Document is not initialized.");
//				}
//			});
//		} catch (Exception e) {
//			System.err.println("[ intellij (onMessage) ] Error processing message: " + e.getMessage());
//		}
	}

	// 클라이언트 연결이 종료되었을 때 호출되는 메서드
	@Override
	public void onClose(WebSocket conn, int code, String reason, boolean remote) {
		System.out.println("클라이언트 연결 종료: " + conn.getRemoteSocketAddress());
	}

	// 오류가 발생했을 때 호출되는 메서드
	@Override
	public void onError(WebSocket conn, Exception ex) {
		System.out.println("오류 발생: " + ex.getMessage());
	}

	// 서버가 정상적으로 실행되었을 때 호출되는 메서드
	@Override
	public void onStart() {
		System.out.println("WebSocket 서버가 시작되었습니다!");
	}

	// 서버 실행 메서드 (이 메서드는 외부에서 호출하여 서버를 시작할 수 있도록 함)
	public void startServer() {
		this.start();
		System.out.println("WebSocket 서버가 1234번 포트에서 리스닝 중입니다.");
	}

	private void openEditorForFileType(VirtualFile virtualFile) {
		if ("md".equalsIgnoreCase(virtualFile.getExtension())) {
			// .md 확장자를 가진 마크다운 파일에 한정하여 커스텀 에디터를 불러오기
			FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(project, virtualFile);
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

}