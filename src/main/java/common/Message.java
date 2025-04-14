package common;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum MessageType {
        SIGNUP, LOGIN, UPLOAD, DOWNLOAD, SEARCH,
        ACCESS_PROFILE, FOLLOW, UNFOLLOW,
        HANDSHAKE, ACK, NACK, DIAGNOSTIC,
        SYNCHRONIZATION, FILE_CHUNK, FILE_END
    }

    private MessageType type;
    private String senderId;
    private String payload;

    public Message(MessageType type, String senderId, String payload) {
        this.type = type;
        this.senderId = senderId;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "Message[type=" + type + ", sender=" + senderId + ", payload=" + payload + "]";
    }
}
