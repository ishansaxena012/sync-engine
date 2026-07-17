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

    const operation = {
        type: "MOVE_OBJECT",
        boardId: boardId,
        userId: "33333333-3333-3333-3333-333333333333",
        timestamp: new Date().toISOString(),
        objectId: "44444444-4444-4444-4444-444444444444",
        x: 120,
        y: 240
    };

    stompClient.publish({
        destination:
            "/app/boards/" +
            boardId +
            "/operations",
        headers: {
            "content-type": "application/json"
        },
        body: JSON.stringify(operation)
    });
    log("📤 MOVE_OBJECT operation sent");
}

function sendCreate() {

    const operation = {

        type: "CREATE_OBJECT",

        boardId: boardId,

        userId: "33333333-3333-3333-3333-333333333333",

        timestamp: new Date().toISOString(),

        canvasObject: {

            id: crypto.randomUUID(),

            boardId: boardId,

            type: "RECTANGLE",

            x: 200,

            y: 150,

            rotation: 0,

            zIndex: 1,

            payload: "{\"width\":120,\"height\":80}",

            createdBy: "33333333-3333-3333-3333-333333333333",

            version: 1
        }

    };

    stompClient.publish({

        destination: "/app/boards/" + boardId + "/operations",

        headers: {
            "content-type": "application/json"
        },

        body: JSON.stringify(operation)

    });

}