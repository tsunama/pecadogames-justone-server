package ch.uzh.ifi.seal.soprafs20.rest.dto;

public class InviteGetDTO {
    private Long lobbyId;

    private String lobbyName;

    private boolean voiceChat;

    private String privateKey;

    public Long getLobbyId() { return lobbyId; }

    public void setLobbyId(long lobbyId) { this.lobbyId = lobbyId; }

    public String getLobbyName() {
        return lobbyName;
    }

    public void setLobbyName(String lobbyName) {
        this.lobbyName = lobbyName;
    }

    public boolean isVoiceChat() {
        return voiceChat;
    }

    public void setVoiceChat(boolean voiceChat) {
        this.voiceChat = voiceChat;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }
}