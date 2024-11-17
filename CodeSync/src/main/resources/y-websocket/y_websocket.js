// y_websocket.js
const fs = require("fs");

let parsedArgs;

let Y, WebsocketProvider;
let provider, ydoc, wsUrl;

const jsonFilePath = process.argv[2];

// WebSocket을 전역에 할당
const WebSocket = require("ws");
globalThis.WebSocket = WebSocket;

console.log(
  '[ vscode ] Congratulations, your extension "codesync" is now active!'
);

fs.readFile(jsonFilePath, "utf8", async (err, data) => {
  if (err) {
    console.error("[ vscode ] Error reading the JSON file:", err);
    process.exit(1);
  }

  // JSON 데이터를 파싱
  parsedArgs = JSON.parse(data);

  await loadYjsModules();

  // 원하는 함수 호출 예시
  await initializeConnection(
    parsedArgs.fileContent,
    parsedArgs.host,
    parsedArgs.fileName
  );
});

async function loadYjsModules() {
  const Yjs = await import("yjs");
  Y = Yjs;
  WebsocketProvider = (await import("y-websocket")).WebsocketProvider;
}

async function initializeConnection(fileContent, host, fileName) {
  ydoc = new Y.Doc();
  wsUrl = "ws://" + host + ":5000"; // 서버 주소
  console.log("이야 이거 실행되는 거 맞나?");
  console.log(host);

  try {
    console.log("[ vscode ] 웹 소켓 연결 중...");
    provider = new WebsocketProvider(wsUrl, host, ydoc);
    console.log("[ vscode ] provider 생성 완료");
    console.log("[ vscode ] WebsocketProvider created successfully.");
    provider.on("status", (event) => {
      console.log(`[ vscode ] WebsocketProvider status: ${event.status}`);
    });
    provider.on("synced", (isSynced) => {
      if (isSynced) {
        console.log("[ vscode ] WebsocketProvider is synced with server.");
      }
    });
    provider.on("connection-close", () => {
      console.log("[ vscode ] WebsocketProvider connection closed.");
    });
  } catch (error) {
    console.error("[ vscode ] Failed to create WebsocketProvider:", error);
    return; // 오류 발생 시 이후 코드 실행 중단
  }

  // yText에 Java에서 전달받은 내용을 삽입
  const yText = ydoc.getText(fileName);
  // yText.insert(0, fileContent);

  const wsClient = new WebSocket("ws://localhost:1234");

  // WebSocket이 열린 후, 메시지를 보낼 준비가 되면
  wsClient.onopen = function () {
    console.log("[ vscode ] WebSocket 서버와 연결되었습니다.");
  };

  const messageQueue = [];
  let isProcessingQueue = false;

  // WebSocket이 메시지를 수신했을 때
  wsClient.onmessage = async function (event) {
    console.log(
      `[ vscode ] wsClient onmessage => ${JSON.stringify(
        JSON.parse(event.data)
      )}`
    );
    console.log(`[ vscode ] isProcessingQueue  => ${isProcessingQueue}`);

    await applyChangeToText(event);
    // messageQueue.push(event);

    // if (!isProcessingQueue) {
    //   await processQueue();
    // }
  };

  wsClient.onclose = function (event) {
    console.log("[ vscode ] WebSocket closed:", event);
  };

  wsClient.onerror = function (error) {
    console.error("[ vscode ] WebSocket error:", error);
  };

  // 큐의 변경 사항을 순차적으로 처리
  async function processQueue() {
    if (isProcessingQueue) return;
    isProcessingQueue = true;

    while (messageQueue.length > 0) {
      const event = messageQueue.shift();
      console.log(`[ vscode ] shifted!`);
      await applyChangeToText(event);
      console.log(`[ vscode ] applied changes!`);
    }

    console.log(
      `[ vscode ] isProcessingQueue will change soon! ${isProcessingQueue}`
    );
    isProcessingQueue = false;
    console.log(
      `[ vscode ] isProcessingQueue changed to false! ${isProcessingQueue}`
    );
  }

  async function applyChangeToText(event) {
    try {
      const parsedMessage = JSON.parse(event.data);
      const startOffset = parsedMessage[0].startOffset;
      const endOffset = parsedMessage[0].endOffset;
      const newText = parsedMessage[0].newText;

      console.log(
        `[ vscode ] parsedMessage in applyChangeToText => ${JSON.stringify(
          parsedMessage
        )}`
      );

      if (endOffset > startOffset) {
        yText.delete(startOffset, endOffset - startOffset);
      }
      console.log(`[ vscode ] delete fin. yText: ${yText.toString()}`);
      yText.insert(startOffset, newText);
      console.log(`[ vscode ] insert fin. yText: ${yText}`);
      console.log(
        "[ vscode ] startOffset type:",
        typeof startOffset,
        startOffset
      );
      console.log("[ vscode ] newText type:", typeof newText, newText);
    } catch (error) {
      console.error("[ vscode ] Error parsing WebSocket message: ", error);
    }
  }

  yText.observe((event) => {
    event.delta.forEach((delta, index) => {
      console.log(`[ vscode ] Delta operation ${index}:`, delta);
      console.log(`[ vscode ] yText transaction: `, event.transaction.local);
      const message = yText.toString();

      // message.replace("\r\n", "\n");

      if (delta.insert || delta.delete) {

        if (
          !event.transaction.local &&
          wsClient.readyState === WebSocket.OPEN
        ) {
          wsClient.send(message);
        }
      }
    });

    //   const message = yText.toString();
    //   console.log(`[ vscode ] observer message: ${message}`);
    //   try {
    //     if (wsClient.readyState === WebSocket.OPEN) {
    //       wsClient.send(message);
    //     }
    //   } catch (error) {
    //     console.error("[ vscode ] WebSocket send error:", error);
    //   }
  });
}

function cleanupResources() {
  console.log(`[ vscode ] ydoc in cleanupResource: ${ydoc}`);

  if (provider) {
    provider.disconnect();
    console.log(
      `[ vscode ] Websocket disconnected for file : ${parsedArgs.fileName}`
    );
  }

  if (ydoc) ydoc.destroy();
}

// Cleanup on exit
function setupExitHandlers() {
  process.on("exit", cleanupResources);
  process.on("SIGINT", () => {
    cleanupResources();
    process.exit();
  });
  process.on("SIGTERM", () => {
    cleanupResources();
    process.exit();
  });
}

// Call the setup function to handle exits
setupExitHandlers();
