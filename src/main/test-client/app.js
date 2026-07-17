let stompClient = null;

const boardId = "11111111-1111-1111-1111-111111111111";
const USER_ID = "33333333-3333-3333-3333-333333333333";

function log(message) {
    const messages = document.getElementById("messages");
    messages.innerHTML += `<p>${message}</p>`;
    messages.scrollTop = messages.scrollHeight;
}

let lastCreatedObjectId = null;


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
            const operation = JSON.parse(message.body);
            if (operation.type === "CREATE_OBJECT") {
                lastCreatedObjectId = operation.canvasObject.id;
                console.log("Latest Object:", lastCreatedObjectId);
            }
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
        userId: USER_ID,
        timestamp: new Date().toISOString(),
        objectId: lastCreatedObjectId,
        x: 500,
        y: 300
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
    log("📦 MOVE_OBJECT operation sent");
}

function sendCreate() {

    const operation = {

        type: "CREATE_OBJECT",
        boardId: boardId,
        userId: USER_ID,
        timestamp: new Date().toISOString(),

        canvasObject: {
            id: crypto.randomUUID(),
            boardId: boardId,
            type: "RECTANGLE",
            x: 200,
            y: 150,
            rotation: 0,
            zIndex: 1,
            version: 1,
            createdBy: USER_ID,
            payload: {
                type: "RECTANGLE",
                width: 120,
                height: 80,
                cornerRadius: 8,
                fillColor: "#F4B400",
                strokeColor: "#000000"
            }
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

function sendRotate() {

    if (!lastCreatedObjectId) {
        log("❌ No object available.");
        return;
    }

    const operation = {

        type: "ROTATE_OBJECT",

        boardId,

        userId: USER_ID,

        timestamp: new Date().toISOString(),

        objectId: lastCreatedObjectId,

        rotation: 45

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

    log("📦 ROTATE_OBJECT operation sent");

}

function sendDelete() {
    if (!lastCreatedObjectId) {
        log("❌ No object available.");
        return;
    }

    const operation = {
        type: "DELETE_OBJECT",
        boardId: boardId,
        userId: USER_ID,
        timestamp: new Date().toISOString(),
        objectId: lastCreatedObjectId
    };

    stompClient.publish({
        destination: "/app/boards/" + boardId + "/operations",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(operation)
    });
    log("📦 DELETE_OBJECT operation sent");
}

function sendChangePayload() {
    if (!lastCreatedObjectId) {
        log("❌ No object available.");
        return;
    }

    const operation = {
        type: "CHANGE_PAYLOAD",
        boardId: boardId,
        userId: USER_ID,
        timestamp: new Date().toISOString(),
        objectId: lastCreatedObjectId,
        payload: {
            type: "RECTANGLE",
            width: 200,
            height: 150,
            cornerRadius: 12,
            fillColor: "#00FF00",
            strokeColor: "#FF0000"
        }
    };

    stompClient.publish({
        destination: "/app/boards/" + boardId + "/operations",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(operation)
    });
    log("📦 CHANGE_PAYLOAD operation sent");
}