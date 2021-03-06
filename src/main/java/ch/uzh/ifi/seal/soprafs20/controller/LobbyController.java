package ch.uzh.ifi.seal.soprafs20.controller;

import ch.uzh.ifi.seal.soprafs20.constant.AvatarColor;
import ch.uzh.ifi.seal.soprafs20.entity.*;
import ch.uzh.ifi.seal.soprafs20.exceptions.ConflictException;
import ch.uzh.ifi.seal.soprafs20.exceptions.NotAcceptableException;
import ch.uzh.ifi.seal.soprafs20.exceptions.NotFoundException;
import ch.uzh.ifi.seal.soprafs20.exceptions.UnauthorizedException;
import ch.uzh.ifi.seal.soprafs20.rest.dto.*;
import ch.uzh.ifi.seal.soprafs20.rest.mapper.DTOMapper;
import ch.uzh.ifi.seal.soprafs20.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;



@RestController
public class LobbyController {
    private final LobbyService lobbyService;
    private final UserService userService;
    private final PlayerService playerService;
    private final ChatService chatService;
    private final MessageService messageService;
    private final GameService gameService;
    private final LobbyScoreService lobbyScoreService;

    LobbyController(LobbyService lobbyService, UserService userService, PlayerService playerService,
                    LobbyScoreService lobbyScoreService, ChatService chatService, MessageService messageService, GameService gameService){
        this.lobbyService = lobbyService;
        this.userService = userService;
        this.playerService = playerService;
        this.chatService = chatService;
        this.messageService = messageService;
        this.gameService = gameService;
        this.lobbyScoreService = lobbyScoreService;
    }


    /**
     * Create a lobby given the LobbyPostDTO from client
     *
     * @return - header with location of lobby if the lobby is set to public
     *         - if the lobby is private, returns header with lobby location and privateKey in body
     */
    @CrossOrigin(exposedHeaders = "Location")
    @PostMapping(path = "/lobbies", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public ResponseEntity<Object> createLobby(@RequestBody LobbyPostDTO lobbyPostDTO) {
        Lobby createdLobby;
        // convert API user to internal representation
        Lobby userLobby = DTOMapper.INSTANCE.convertLobbyPostDTOtoEntity(lobbyPostDTO);
        User host = userService.getUser(lobbyPostDTO.getHostId());
        //convert host from user to player
        Player hostAsPlayer = playerService.convertUserToPlayer(host);
        // create lobby
        try {
            createdLobby = lobbyService.createLobby(userLobby, hostAsPlayer);

            //create chat for lobby
            chatService.createChat(createdLobby.getLobbyId());

            URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{lobbyId}")
                    .buildAndExpand(createdLobby.getLobbyId()).toUri();
            return ResponseEntity.created(location).build();

        } catch (NotAcceptableException e){
            playerService.deletePlayer(hostAsPlayer);
            throw new NotAcceptableException("Could not create lobby");
        }
    }

    @GetMapping(path = "/lobbies", produces = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<LobbyGetDTO> getAllLobbies(@RequestParam("token") String token) {
        userService.getUserByToken(token);
        // fetch all lobbies in the internal representation
        List<Lobby> lobbies = lobbyService.getLobbies();
        List<LobbyGetDTO> lobbyGetDTOs = new ArrayList<>();

        // convert each lobby to the API representation
        for (Lobby lobby : lobbies) {
            lobbyGetDTOs.add(DTOMapper.INSTANCE.convertEntityToLobbyGetDTO(lobby));
        }
        return lobbyGetDTOs;
    }

    @PostMapping(path = "lobbies/{lobbyId}", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @ResponseBody
    public ResponseEntity<Object> createGame(@PathVariable long lobbyId, @RequestBody GamePostDTO gamePostDTO) {
        Lobby lobby = lobbyService.getLobby(lobbyId);
        int numberOfPlayersAndBots = lobby.getCurrentNumBots() + lobby.getCurrentNumPlayers();
        if(numberOfPlayersAndBots < 3 || numberOfPlayersAndBots > 7){
            throw new ConflictException("Invalid amount of players to start the game");
        }
        Game createdGame = gameService.createGame(lobby, gamePostDTO);
        gameService.setTimer(createdGame);
        gameService.timer(createdGame);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/game")
                .build().toUri();
        return ResponseEntity.created(location).build();
    }

    @PutMapping(path = "/lobbies/{lobbyId}", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ResponseBody
    public void updateLobby(@PathVariable long lobbyId, @RequestBody LobbyPutDTO lobbyPutDTO){
        Lobby lobby = lobbyService.getLobby(lobbyId);
        lobbyService.updateLobby(lobby,lobbyPutDTO);
    }

    @PutMapping(path = "/lobbies/{lobbyId}/kick", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ResponseBody
    public void kickPlayer(@PathVariable long lobbyId, @RequestBody LobbyPutDTO lobbyPutDTO){
        Lobby lobby = lobbyService.getLobby(lobbyId);
        Player playerToKick = new Player();
        if(lobbyPutDTO.getPlayerToKickId() == 0L) {
            playerToKick.setId(0L);
        }
        else {
            playerToKick = playerService.getPlayer(lobbyPutDTO.getPlayerToKickId());
        }
        lobbyService.kickPlayers(lobby,playerToKick);
    }

    @GetMapping(path = "/lobbies/{lobbyId}", produces = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public LobbyGetDTO getLobby(@PathVariable long lobbyId) {
        Lobby lobby = lobbyService.getLobby(lobbyId);

        LobbyGetDTO lobbyGetDTO = DTOMapper.INSTANCE.convertEntityToLobbyGetDTO(lobby);
        for(int i = 0; i<lobby.getCurrentNumBots(); i++) {
            Player botAsPlayer = new Player();
            botAsPlayer.setId(0L);
            botAsPlayer.setUsername("bot!");
            botAsPlayer.setAvatarColor(AvatarColor.BOT);
            botAsPlayer.setScore(-1);
            lobbyGetDTO.addPlayersInLobby(botAsPlayer);
        }

        return lobbyGetDTO;
    }

    @PutMapping(path = "/lobbies/{lobbyId}/invitations", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ResponseBody
    public void invitePlayerToLobby(@PathVariable long lobbyId, @RequestBody InvitePutDTO invitePutDTO){
        Lobby lobby = lobbyService.getLobby(lobbyId);
        User host = userService.getUser(invitePutDTO.getUserId());
        User userToInvite = userService.getUser(invitePutDTO.getUserToInviteId());
        userService.addLobbyInvite(userToInvite,lobby,host);
    }

    @PutMapping(path = "/lobbies/{lobbyId}/acceptances", consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public LobbyGetDTO handleLobbyInvite(@PathVariable long lobbyId, @RequestBody LobbyAcceptancePutDTO lobbyAcceptancePutDTO) {
        Lobby lobby = lobbyService.getLobby(lobbyId);
        if(userService.acceptOrDeclineLobbyInvite(lobby, lobbyAcceptancePutDTO)) {
            User user = userService.getUser(lobbyAcceptancePutDTO.getAccepterId());
            playerService.checkPlayerToken(user.getToken(), lobbyAcceptancePutDTO.getAccepterToken());
            Player player = playerService.convertUserToPlayer(user);
            lobbyService.addPlayerToLobby(lobbyAcceptancePutDTO.getAccepterToken(), player, lobby);
        }

        return DTOMapper.INSTANCE.convertEntityToLobbyGetDTO(lobby);
    }

    @GetMapping(path = "/lobbies/{lobbyId}/chat", produces = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public ChatGetDTO getChatMessages(@PathVariable long lobbyId,@RequestParam("token") String token) {
        boolean found = false;
        Lobby lobby = lobbyService.getLobby(lobbyId);
        for (Player player : lobby.getPlayersInLobby()){
            if(player.getToken().equals(token)){
                found = true;
                break;
            }
        }
        if(!found) {
            throw new UnauthorizedException("This player is not allowed to access this chat history!");
        }
        Chat chat = chatService.getChat(lobbyId);
        return DTOMapper.INSTANCE.convertEntityToChatGetDTO(chat);
    }

    @PutMapping(path = "lobbies/{lobbyId}/chat", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ResponseBody
    public void addChatMessage(@PathVariable long lobbyId, @RequestBody MessagePutDTO messagePutDTO) {
        Message message = DTOMapper.INSTANCE.convertMessagePutDTOtoEntity(messagePutDTO);
        message = messageService.createMessage(message);
        User author  = userService.getUser(messagePutDTO.getPlayerId());
        Lobby lobby = lobbyService.getLobby(lobbyId);
        if(lobby.isGameStarted()) {
            Game game = gameService.getGame(lobbyId);
            if(isContained(game.getCurrentWord(), messagePutDTO.getMessage())) {
                message.setText("I'm a cheetah!");
            }
        }
        if(messagePutDTO.getMessage().length() <= 51) {
            chatService.addChatMessage(lobby, author.getToken(), message);
        }
    }

    @PutMapping(path = "/lobbies/{lobbyId}/joins", consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public void joinLobby(@PathVariable long lobbyId, @RequestBody JoinLeavePutDTO joinLeavePutDTO){
        Lobby lobby = lobbyService.getLobby(lobbyId);
        User user = userService.getUser(joinLeavePutDTO.getPlayerId());
        //convert user into player
        playerService.checkPlayerToken(user.getToken(), joinLeavePutDTO.getPlayerToken());
        if(lobby.isPrivate()){
            throw new UnauthorizedException("Lobby is private, unable to join!");
        }
        Player player = playerService.convertUserToPlayer(user);
        try{
            lobbyService.addPlayerToLobby(joinLeavePutDTO.getPlayerToken(), player, lobby);
        } catch (ConflictException e){
            playerService.deletePlayer(player);
            throw e;
        }

    }

    @PutMapping(path = "lobbies/{lobbyId}/rageQuits", consumes = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public void leaveLobby(@PathVariable long lobbyId, @RequestBody JoinLeavePutDTO joinLeavePutDTO){
        Lobby lobby = lobbyService.getLobby(lobbyId);
        Player playerToBeRemoved = playerService.getPlayer(joinLeavePutDTO.getPlayerId());
        lobbyService.removePlayerFromLobby(playerToBeRemoved, lobby);
        playerService.deletePlayer(playerToBeRemoved);
    }

    @GetMapping(path = "lobbies/scores",produces = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public List<LobbyScoreGetDTO> getLobbyScores(@RequestParam("token") String token){
        try {
            userService.getUserByToken(token);
        } catch (NotFoundException e){
            throw new NotFoundException("Cant get lobby scores as " + e.getMessage().toLowerCase());
        }
        List<LobbyScore> lobbyScores;

        lobbyScores = lobbyScoreService.getLobbyScoresByScore();
        List<LobbyScoreGetDTO> lobbyScoreGetDTOs = new ArrayList<>();
        for(LobbyScore lb: lobbyScores){
            lobbyScoreGetDTOs.add(DTOMapper.INSTANCE.convertEntityToLobbyScoreGetDTO(lb));
        }
        return lobbyScoreGetDTOs;
    }

    public boolean isContained(String aString, String bString) {
        return (aString.toLowerCase().contains(bString.toLowerCase()) || bString.toLowerCase().contains(aString.toLowerCase()));
    }


}
