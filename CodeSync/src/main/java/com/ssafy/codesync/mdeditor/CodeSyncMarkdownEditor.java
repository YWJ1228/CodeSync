package com.ssafy.codesync.mdeditor;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefClient;
import com.intellij.ui.jcef.JBCefJSQuery;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JComponent;
import org.cef.CefSettings.LogSeverity;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CodeSyncMarkdownEditor implements FileEditor {

  private final VirtualFile file;
  private final Document document;
  private final JBCefBrowser browser;
  private JBCefJSQuery saveContentQuery;

  private boolean isUpdating = false;
  private Timer debounceTimer;

  // editor.html에서 yjs 웹소켓 연결 시 필요
//  private final boolean isRemoteFile;
//  private String serverIp = "3.39.217.167";
//  private String webSocketPort = "5000";
//  private String fileName;

  public CodeSyncMarkdownEditor(@NotNull VirtualFile file) {
    // 1. file
    this.file = file;

    // 2. document
    try {
      this.document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        // 도큐먼트 리스너 (필요 없을 시 삭제)
        // 인텔리제이 편집기 화면에서 저장이 안 된 상태에서도 즉각적으로 tui 에디터 화면에 내용 반영
        document.addDocumentListener(new DocumentListener() {
          @Override
          public void documentChanged(@NotNull DocumentEvent event) {
            // 한글 완성 때문에 타이머로 업데이트
            // 이전 타이머가 있을 경우 취소
            if (debounceTimer != null) {
              debounceTimer.cancel();
            }
            // 새로운 타이머 설정 (300ms 정도의 딜레이 후 반영)
            debounceTimer = new Timer();
            debounceTimer.schedule(new TimerTask() {
              @Override
              public void run() {
                String updatedContent = document.getText();
                System.out.println("도큐먼트 갱신 loadMarkdownContentToBrowser");
                loadMarkdownContentToBrowser(updatedContent);
              }
            }, 300); // 300ms 딜레이 설정
          }
        });
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    // 원격 파일 웹소켓 연결 세팅 필요한가?
    // editor.html에서 yjs 웹소켓 연결 시 필요
//    String codeSyncPath = (System.getProperty("java.io.tmpdir") + "CodeSync").replace("\\", "/");
//    String filePath = file.getPath().replace("\\", "/"); // file.getPath()도 일관되게 /로 변환
//    this.fileName = file.getName(); // 파일명만 추출
//    this.isRemoteFile = filePath.startsWith(codeSyncPath);

    // 3. browser
    // HTML 파일을 임시 파일로 추출하여 경로 설정
    String tempFilePath = extractResourceToTemp("editor/editor.html");
    if (tempFilePath != null) {
      this.browser = new JBCefBrowser("file://" + tempFilePath);

      // 4. saveContentQuery
      // html의 자바스크립트와 MarkdownEditor 자바 연결할 브릿지 생성
      saveContentQuery = JBCefJSQuery.create(browser);

      JBCefClient client = browser.getJBCefClient();
      // JavaScript 콘솔 출력을 Java 콘솔로 리디렉션
      // 배포 시 코드 삭제
      client.addDisplayHandler(new CefDisplayHandlerAdapter() {
        @Override
        public boolean onConsoleMessage(CefBrowser browser, LogSeverity level, String message,
            String source, int line) {
          System.out.printf("JS Console [%s:%d]: %s\n", source, line, message);
          return false;
        }
      }, browser.getCefBrowser());
      // 브라우저 로드 완료 후,
      client.addLoadHandler(new CefLoadHandlerAdapter() {
        @Override
        public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
          System.out.println("Browser load complete, setting up JavaScript bindings...");
          System.out.println("첫 초기화 loadMarkdownContentToBrowser");
          loadMarkdownContentToBrowser(); // md 파일 내용 불러오기
          addFileChangeListener(); // 원본 md 파일 변경 내용 반영하기
          setupJavaScriptBindings(); // 로드 완료 후 JavaScript 바인딩 설정
        }
      }, browser.getCefBrowser());

      System.out.println("불러오기");
    } else {
      this.browser = new JBCefBrowser();
    }
  }

  // --------------------------------------------------------------------------------------------
  // Listener 등록
  // --------------------------------------------------------------------------------------------

  // 문서 변경 감지(저장할 때마다 반응) 리스너 추가
  private void addFileChangeListener() {
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        if (event.getFile().equals(file)) {
          System.out.println("문서 변경 loadMarkdownContentToBrowser");
          loadMarkdownContentToBrowser();
        }
      }
    });
  }

  // --------------------------------------------------------------------------------------------
  // 저장 및 불러오기
  // HTML의 스크립트와 자바를 연결하여, HTML 에디터에서 작성된 내용과 md 파일에 작성된 내용을 연동시킴
  // --------------------------------------------------------------------------------------------

  // md 도큐먼트 내용을 HTML의 에디터로 로드하는 메서드
  private void loadMarkdownContentToBrowser(String content) {
    if (isUpdating) {
      System.out.println("업데이트 중이라 HTML 갱신 못 해");
      return;
    }
    isUpdating = true;
    String escapedContent = escapeForJS(content);
    String jsCode = "window.loadMarkdownContent(" + escapedContent + ");";
    browser.getCefBrowser().executeJavaScript(jsCode, browser.getCefBrowser().getURL(), 0);
    System.out.println("도큐먼트 체인지.");
    isUpdating = false;
  }

  // md 파일 내용을 HTML의 에디터로 로드하는 메서드
  private void loadMarkdownContentToBrowser() {
    if (isUpdating) {
      System.out.println("업데이트 중이라 HTML 갱신 못 해");
      return;
    }

    try {
      // 플래그 설정: 업데이트 중임을 표시
      isUpdating = true;
      System.out.println("HTML로 업데이트 중 loadMarkdownContentToBrowser");

      // md 파일의 내용을 문자열로 읽기
      String content = new String(file.contentsToByteArray(), StandardCharsets.UTF_8);
      content = content.replaceAll("\r\n|\r|\n", "\n");
      String escapedContent = escapeForJS(content);
      String jsCode = "window.loadMarkdownContent(" + escapedContent + ");";
      browser.getCefBrowser().executeJavaScript(jsCode, browser.getCefBrowser().getURL(), 0);
      System.out.println("Executed JavaScript to load content in editor with delay.");
    } catch (Exception e) {
      System.out.println("Failed to load content from md file:");
      e.printStackTrace();
    } finally {
      // 업데이트 완료 후 플래그 해제
      isUpdating = false;
      System.out.println("HTML 업데이트 끝");
    }
  }

  // JavaScript에서 안전하게 사용할 수 있도록 특수 문자를 모두 이스케이프 처리
  private String escapeForJS(String content) {
    return "\"" + content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        .replace("\r", "\\r").replace("\t", "\\t") + "\"";
  }

  // HTML로부터 받은 내용을 md 파일에 저장하는 메서드
  public void saveMarkdownContent(String content) {
    // 업데이트 중인 상태면 저장을 수행하지 않음
    if (isUpdating) {
      System.out.println("업데이트 중이라 md 갱신 못 해");
      return;
    }
    if (content.equals(document.getText())) {
      System.out.println("내용 같아");
      return;
    }

    // VirtualFile이 유효하고 쓰기 가능 상태인지 확인
    if (!file.isWritable()) {
      System.out.println("The file is not writable.");
      return;
    }
    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        // 플래그 설정: 업데이트 중임을 표시
        isUpdating = true;
        System.out.println("md로 업데이트 중 saveMarkdownContent");

        // IntelliJ의 자동 리프레시 때문에 주기적으로 Document 동기화가 필요함
        Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
          // Document API를 통해 내용을 설정하고 저장
          document.setText(content);
          FileDocumentManager.getInstance().saveDocument(document);
          System.out.println("File saved successfully with Document API.");
        } else {
          // Document가 없을 경우 VfsUtil 사용
          VfsUtil.saveText(file, content);
          System.out.println("File saved successfully with VfsUtil.");
        }
      } catch (Exception e) {
        System.out.println("Failed to save file content:");
        e.printStackTrace();
      } finally {
        // 업데이트 완료 후 플래그 해제
        isUpdating = false;
        System.out.println("md 업데이트 끝");
      }
    });
  }

  // html 스크립트와 자바 바인딩
  private void setupJavaScriptBindings() {
    saveContentQuery.addHandler((String message) -> {
      // 업데이트 중인 상태면 추가 저장 수행하지 않음
      if (isUpdating) {
        return null;
      }

      System.out.println("Received content from JavaScript: " + message);
      saveMarkdownContent(message);
      return null;
    });
    // JavaScript에 전달할 코드 추가
    String jsCode1 = String.format("window.saveContentQuery = function(content) { %s(content); };",
        saveContentQuery.inject("content"));
    browser.getCefBrowser().executeJavaScript(jsCode1, browser.getCefBrowser().getURL(), 0);

    // editor.html에서 yjs 웹소켓 연결 시 필요
//    String jsCode2 = String.format(
//        "window.isRemoteFile = %b; window.serverIp = '%s'; window.webSocketPort = '%s'; window.fileName = '%s';",
//        isRemoteFile, serverIp, webSocketPort, fileName
//    );
//    browser.getCefBrowser().executeJavaScript(jsCode2, browser.getCefBrowser().getURL(), 0);
  }

  // --------------------------------------------------------------------------------------------
  // md 파일을 임시파일로
  // --------------------------------------------------------------------------------------------

  // 리소스를 임시 파일로 추출
  private String extractResourceToTemp(String resourcePath) {
    try {
      // 리소스를 InputStream으로 가져오기
      InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
      if (resourceStream == null) {
        return null;
      }
      // 임시 파일 생성
      File tempFile = File.createTempFile("editor", ".html");
      tempFile.deleteOnExit(); // JVM 종료 시 임시 파일 삭제
      // 임시 파일에 리소스 복사
      try (FileOutputStream outStream = new FileOutputStream(tempFile)) {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = resourceStream.read(buffer)) != -1) {
          outStream.write(buffer, 0, bytesRead);
        }
      }
      return tempFile.getAbsolutePath(); // 임시 파일 경로 반환
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return browser.getComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return browser.getComponent();
  }

  @NotNull
  @Override
  public String getName() {
    return "Markdown Editor";
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    // 필요한 경우 상태를 복원할 로직을 추가할 수 있습니다.
  }

  @Override
  public boolean isModified() {
    return false; // 파일이 수정되지 않은 상태로 설정
  }

  @Override
  public boolean isValid() {
    return true; // 에디터가 유효한 상태로 설정
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    // 필요시 PropertyChangeListener 로직 추가
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    // 필요시 PropertyChangeListener 로직 추가
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null; // 현재 위치는 지원하지 않음
  }

  @NotNull
  @Override
  public VirtualFile getFile() {
    return file; // 마크다운 파일을 반환하도록 구현
  }

  @Override
  public void dispose() {
    if (saveContentQuery != null) {
      saveContentQuery.clearHandlers();
      saveContentQuery = null;
    }
    browser.dispose(); // 브라우저 자원 해제
  }

  @Override
  public <T> @Nullable T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
  }
}
