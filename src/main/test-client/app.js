let stompClient = null;

const boardId = "11111111-1111-1111-1111-111111111111";

function log(message) {
    const messages = document.getElementById("messages");

    messages.innerHTML += `<p>${message}</p>`;

    messages.scrollTop = messages.scrollHeight;
}

function connect() {

    const socket = new WebSocket("ws://localhost:8080/ws");
    stompClient = new StompJs.Client({

        webSocketFactory: () => socket,

        debug: console.log,

        reconnectDelay: 5000,

        onConnect: () => {

            log("✅ Connected");

        }

    });

    stompClient.activate();
}

function subscribe() {

    if (!stompClient || !stompClient.connected) {
        log("❌ Not connected");
        return;
    }

    stompClient.subscribe(
        "/topic/boards/" + boardId,
        (message) => {
            log("📩 " + message.body);
        }
    );

    log("✅ Subscribed");
}

function sendTest() {

    if (!stompClient || !stompClient.connected) {
        log("❌ Not connected");
        return;
    }

    stompClient.publish({
        destination: "/app/boards/" + boardId + "/operations",
        body: "Hello SyncCanvas"
    });

    log("📤 Sent");
}