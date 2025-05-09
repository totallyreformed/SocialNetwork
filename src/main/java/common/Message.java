package common;

import java.io.Serializable;

/**
 * Represents a serializable message exchanged between client and server,
 * encapsulating a message type, sender identifier, and message payload.
 */
public class Message implements Serializable {
    /**
     * Unique identifier for serialization compatibility.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Enumeration of all supported message types in the protocol.
     */
    public enum MessageType {
        SIGNUP, LOGIN, UPLOAD, DOWNLOAD, SEARCH,
        ACCESS_PROFILE, FOLLOW, UNFOLLOW,
        LIST_FOLLOWERS, LIST_FOLLOWERS_RESPONSE,
        LIST_FOLLOWING, LIST_FOLLOWING_RESPONSE,
        FOLLOW_REQUEST, FOLLOW_RESPONSE,
        HANDSHAKE, ACK, NACK, DIAGNOSTIC,
        FILE_CHUNK, FILE_END,
        AUTH_SUCCESS, AUTH_FAILURE,
        REPOST, COMMENT
    }

    /** Type of this message. */
    private MessageType type;
    /** Identifier of the message sender (client ID). */
    private String senderId;
    /** Content or parameters of the message. */
    private String payload;

    /**
     * Constructs a new Message with the specified type, sender, and payload.
     *
     * @param type      the type of message being sent
     * @param senderId  the identifier of the message sender
     * @param payload   the message content or parameters
     */
    public Message(MessageType type, String senderId, String payload) {
        this.type = type;
        this.senderId = senderId;
        this.payload = payload;
    }

    /**
     * Retrieves the type of this message.
     *
     * @return the MessageType of this message
     */
    public MessageType getType() {
        return type;
    }

    /**
     * Retrieves the identifier of the message sender.
     *
     * @return the client ID of the sender
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * Retrieves the payload content of this message.
     *
     * @return the message payload string
     */
    public String getPayload() {
        return payload;
    }

    /**
     * Returns a string representation of this Message for debugging.
     *
     * @return a string describing the message type, sender, and payload
     */
    @Override
    public String toString() {
        return "Message[type=" + type + ", sender=" + senderId + ", payload=" + payload + "]";
    }
}