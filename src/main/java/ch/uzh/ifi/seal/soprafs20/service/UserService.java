package ch.uzh.ifi.seal.soprafs20.service;

import ch.uzh.ifi.seal.soprafs20.constant.AvatarColor;
import ch.uzh.ifi.seal.soprafs20.constant.UserStatus;
import ch.uzh.ifi.seal.soprafs20.entity.Lobby;
import ch.uzh.ifi.seal.soprafs20.entity.User;
import ch.uzh.ifi.seal.soprafs20.exceptions.*;
import ch.uzh.ifi.seal.soprafs20.repository.UserRepository;
import ch.uzh.ifi.seal.soprafs20.rest.dto.FriendPutDTO;
import ch.uzh.ifi.seal.soprafs20.rest.dto.LobbyAcceptancePutDTO;
import ch.uzh.ifi.seal.soprafs20.rest.dto.UserPutDTO;
import com.fasterxml.jackson.core.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * User Service
 * This class is the "worker" and responsible for all functionality related to the user
 * (e.g., it creates, modifies, deletes, finds). The result will be passed back to the caller.
 */
@Service
@Transactional
public class UserService {

    private final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getUsers() {
        return this.userRepository.findAll();
    }

    public User getUser(Long id) {
        User user;
        Optional<User> optional = userRepository.findById(id);
        if (optional.isPresent()) {
            user = optional.get();
            return user;
        }
        else {
            throw new NotFoundException("Couldn't find user.");
        }
    }

    public User createUser(User newUser) {
        newUser.setToken(UUID.randomUUID().toString());
        newUser.setStatus(UserStatus.OFFLINE);
        newUser.setAvatarColor(getRandomColor());
        newUser.setCreationDate();
        checkUsername(newUser.getUsername());
        checkIfUserExists(newUser);

        // saves the given entity but data is only persisted in the database once flush() is called
        newUser = userRepository.save(newUser);
        userRepository.flush();

        log.debug("Created Information for User: {}", newUser);
        return newUser;
    }

    public User loginUser(User user) {
        User foundUser = userRepository.findByUsername(user.getUsername());
        if (foundUser == null) {
            throw new NotFoundException("user credentials are incorrect!");
        }

        String enteredPassword = user.getPassword();
        String storedPassword = userRepository.findByUsername(user.getUsername()).getPassword();

        if (!enteredPassword.equals(storedPassword)) {
            throw new NotFoundException("user credentials are incorrect!");
        }
        isAlreadyLoggedIn(foundUser);

        foundUser.setToken(UUID.randomUUID().toString());
        foundUser.setStatus(UserStatus.ONLINE);
        log.debug("User {} has logged in.", user);
        return foundUser;
    }

    public void logoutUser(User findUser) {
        User user = getUser(findUser.getId());
        if (user.getStatus() == UserStatus.ONLINE && user.getToken().equals(findUser.getToken())) {
            user.setStatus(UserStatus.OFFLINE);
            user.setToken(null);
            log.debug("User {} has logged out.", user);
        }
        else {
            throw new UnauthorizedException("Logout is not allowed!");
        }
    }

    public void updateUser(User user, UserPutDTO receivedValues) throws JsonParseException {

        if (!user.getUsername().equals(receivedValues.getUsername()) && userRepository.findByUsername(receivedValues.getUsername()) != null) {
            throw new ConflictException("This username already exists.");
        }
        if (!user.getToken().equals(receivedValues.getToken())) {
            throw new UnauthorizedException("You are not allowed to change this user's information!");
        }

        if (receivedValues.getBirthday() != null) {
            user.setBirthday(receivedValues.getBirthday());
        }
        if (receivedValues.getUsername() != null) {
            checkUsername(receivedValues.getUsername());
            user.setUsername(receivedValues.getUsername());
        }

        if (receivedValues.getAvatarColor() != null) {
            checkAvatarColor(receivedValues.getAvatarColor());
            user.setAvatarColor(receivedValues.getAvatarColor());
        }
    }

    public void addFriendRequest(User receiver, User sender) {
        if (userRepository.findByUsername(receiver.getUsername()) == null) {
            String exceptionMessage = "User with id %s does not exist!";
            throw new NotFoundException(String.format(exceptionMessage, receiver.getId().toString()));
        }
        User user = userRepository.findById(sender.getId()).get();
        if (!sender.getToken().equals(user.getToken())) {
            throw new UnauthorizedException("You are not allowed to send a friend request!");
        }
        receiver.setFriendRequests(sender);
    }

    public void acceptOrDeclineFriendRequest(User receiver, FriendPutDTO friendPutDTO) {
        User sender = getUser(friendPutDTO.getSenderID());
        if (receiver.getFriendRequests().contains(sender)) {
            if (friendPutDTO.getAccepted()) {
                receiver.setFriendList(sender);
                sender.setFriendList(receiver);
            }
            receiver.getFriendRequests().remove(sender);
        }
        else {
            throw new NotFoundException(String.format("No friend request from user with id %s was found!", sender.getId().toString()));
        }
    }

    public void addLobbyInvite(User receiver, Lobby lobby, User sender) {
        if (!sender.getToken().equals(lobby.getToken())) {
            throw new UnauthorizedException("User is not authorized to send lobby invites");
        }
        if (sender.getId().equals(receiver.getId())) {
            throw new ConflictException("Cannot invite yourself to the lobby");
        }
        receiver.setLobbyInvites(lobby);
    }

    //TODO: @Mary review my changes to this method
    public void acceptOrDeclineLobbyInvite(Lobby lobby, LobbyAcceptancePutDTO lobbyAcceptancePutDTO) {
        User receiver = getUser(lobbyAcceptancePutDTO.getAccepterId());
        if (!receiver.getToken().equals(lobbyAcceptancePutDTO.getAccepterToken()) || !receiver.getLobbyInvites().contains(lobby)) {
            throw new UnauthorizedException("You are not allowed to accept or decline this lobby invite!");
        }
        receiver.getLobbyInvites().remove(lobby);
        checkIfLobbyIsFull(lobby);
        if (lobbyAcceptancePutDTO.isAccepted()) {
            lobby.addUserToLobby(receiver);
            //update player count
            lobby.setCurrentNumPlayersAndBots(lobby.getCurrentNumPlayersAndBots() + 1);
        }
        else {
            throw new NoContentException("You declined the lobby invite.");
        }
    }

    private void checkIfLobbyIsFull(Lobby lobby) {
        if(lobby.getCurrentNumPlayersAndBots() + 1 > lobby.getMaxPlayersAndBots()) {
            throw new ConflictException("Failed to join lobby: Sorry, the lobby is already full");
        }
    }

    private void checkIfUserExists(User userToBeCreated) {
        User userByUsername = userRepository.findByUsername(userToBeCreated.getUsername());
        String baseErrorMessage = "The %s provided %s not unique. Therefore, the user could not be created!";
        if (userByUsername != null) {
            throw new ConflictException(String.format(baseErrorMessage, "username", "is"));
        }
    }

    public void isAlreadyLoggedIn(User user) {
        if (user.getStatus() == UserStatus.ONLINE) {
            throw new NoContentException("User already logged in!");
        }
    }

    public void checkUsername(String username) {
        if (username.contains(" ") || username.isEmpty() || username.isBlank() || username.length() > 20 || username.trim().isEmpty() || !username.matches("[a-zA-Z_0-9]*")) {
            throw new NotAcceptableException("This is an invalid username. Please choose a username with a maximum length of 20 characters consisting of letters, digits and underscores..");
        }
    }

    private AvatarColor getRandomColor() {
        List<AvatarColor> values = List.of(AvatarColor.values());
        int size = values.size();
        Random random = new Random();

        return values.get(random.nextInt(size));
    }

    private void checkAvatarColor(AvatarColor enteredColor) {
        for (AvatarColor color : AvatarColor.values()) {
            if (color.equals(enteredColor)) {
                return;
            }
        }
        throw new NotAcceptableException("This is an invalid color. Please choose from the following colors: " + Arrays.toString(AvatarColor.values()));
    }
}
