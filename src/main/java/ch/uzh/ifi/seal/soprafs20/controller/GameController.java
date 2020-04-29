package ch.uzh.ifi.seal.soprafs20.controller;

import ch.uzh.ifi.seal.soprafs20.GameLogic.gameStates.GameState;
import ch.uzh.ifi.seal.soprafs20.entity.Game;
import ch.uzh.ifi.seal.soprafs20.entity.Player;
import ch.uzh.ifi.seal.soprafs20.exceptions.BadRequestException;
import ch.uzh.ifi.seal.soprafs20.exceptions.ForbiddenException;
import ch.uzh.ifi.seal.soprafs20.exceptions.UnauthorizedException;
import ch.uzh.ifi.seal.soprafs20.rest.dto.GameGetDTO;
import ch.uzh.ifi.seal.soprafs20.rest.dto.MessagePutDTO;
import ch.uzh.ifi.seal.soprafs20.rest.dto.RequestPutDTO;
import ch.uzh.ifi.seal.soprafs20.rest.mapper.DTOMapper;
import ch.uzh.ifi.seal.soprafs20.service.GameService;
import ch.uzh.ifi.seal.soprafs20.service.InternalTimerService;
import ch.uzh.ifi.seal.soprafs20.service.PlayerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
public class GameController {
    private final PlayerService playerService;
    private final GameService gameService;
    private final InternalTimerService internalTimerService;

    GameController(PlayerService playerService, GameService gameService, InternalTimerService internalTimerService){
        this.playerService = playerService;
        this.gameService = gameService;
        this.internalTimerService = internalTimerService;
    }

    @GetMapping(path = "lobbies/{lobbyId}/game", produces = "application/json")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public GameGetDTO getGame(@PathVariable Long lobbyId, @RequestParam("token") String token) {
        Game game = gameService.getGame(lobbyId);

        GameGetDTO gameGetDTO = DTOMapper.INSTANCE.convertEntityToGameGetDTO(game);

        List<String> tokens = new ArrayList<>();
        for(Player player : game.getPlayers()) {
            tokens.add(player.getToken());
        }
        if(!tokens.contains(token)) {
            throw new UnauthorizedException("You are not allowed to access this game instance!");
        }
        //if guesser requests game, eliminate current word from dto
        if(game.getCurrentGuesser().getToken().equals(token)) {
            gameGetDTO.setCurrentWord(null);
        }
        return gameGetDTO;
    }

    @PutMapping(path = "lobbies/{lobbyId}/game/clue", consumes = "application/json")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ResponseBody
    public void sendClue(@PathVariable long lobbyId, @RequestBody MessagePutDTO messagePutDTO){
        boolean advance;
        Game currentGame = gameService.getGame(lobbyId);
        if(!currentGame.getGameState().equals(GameState.ENTERCLUESSTATE)){
            throw new ForbiddenException("Clues not accepted in current state");
        }
        if(!currentGame.getTimer().isRunning()){
            throw new ForbiddenException("Time ran out!");
        }
        Player player = playerService.getPlayer(messagePutDTO.getPlayerId());
        String clue = messagePutDTO.getMessage();
        advance = gameService.sendClue(currentGame, player, clue);
        if(advance){
            gameService.setStartTime(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()),currentGame);
            gameService.timer(currentGame,GameState.VOTEONCLUESSTATE);
        }
    }

    @GetMapping(path = "lobbies/{lobbyId}/game/word")
    @ResponseStatus(HttpStatus.OK)
    @ResponseBody
    public void pickWord(@PathVariable long lobbyId,@RequestParam("token") String token){
        Game game = gameService.getGame(lobbyId);
        if(!game.getGameState().equals(GameState.PICKWORDSTATE)){
            throw new ForbiddenException("Can't choose word in current state");
        }
        gameService.pickWord(token, game);

        internalTimerService.createInternalTimer(game,60,game.getStartTimeSeconds(),GameState.NLPSTATE);
    }

    @GetMapping(path = "lobbies/{lobbyId}/game/timer")
    @ResponseStatus(HttpStatus.OK)
    public String getTimer(@PathVariable long lobbyId, @RequestParam String token) {
        long currentTime = System.currentTimeMillis();
        boolean found = false;
        Game game = gameService.getGame(lobbyId);
        for(Player p : game.getPlayers()){
            if(p.getToken().equals(token)) {
                found = true;
                break;
            }
        }
        if(!found)
            throw new UnauthorizedException("Not allowed to retrieve timer for this game!");
        if(game.getStartTimeSeconds() == null){
            return "No timer started yet";
        } else {
            return Long.toString(internalTimerService.getTime(game.getTimer()));
        }
    }

    @PutMapping(path = "lobbies/{lobbyId}/game/guess")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ResponseBody
    public void sendGuess(@PathVariable long lobbyId, @RequestBody MessagePutDTO messagePutDTO) {
        long currentTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        Game game = gameService.getGame(lobbyId);
        if(!game.getGameState().equals(GameState.ENTERGUESSSTATE)) {
            throw new ForbiddenException("Can't submit guess in current state!");
        }
        gameService.submitGuess(game, messagePutDTO,currentTimeSeconds);
    }

    @PutMapping(path = "lobbies/{lobbyId}/game/transition")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ResponseBody
    public void startNewRound(@PathVariable long lobbyId, @RequestBody RequestPutDTO requestPutDTO) {
        Game game = gameService.getGame(lobbyId);
        if(!game.getGameState().equals(GameState.TRANSITIONSTATE)) {
            throw new ForbiddenException("Can't start new round in current state!");
        }
        gameService.startNewRound(game, requestPutDTO);
    }

    private String asJsonString(final Object object) {
        try {
            return new ObjectMapper().writeValueAsString(object);
        }
        catch (JsonProcessingException e) {
            throw new BadRequestException(String.format("The request body could not be created.%s", e.toString()));
        }
    }

}
