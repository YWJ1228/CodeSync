package com.ssafy.codesync.state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@State(name = "UserInfo", storages = @Storage("userInfo.xml"))
public class UserInfo implements PersistentStateComponent<UserInfo.State> {
    public static class State {
        public List<User> users = new ArrayList<>();
    }

    private State state = new State();

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public void addUser(User user) {
        this.state.users.add(user);
    }

    public List<User> getUsers() {
        return state.users;
    }

    public User getUserByServerIP(String serverIP) {
        for (User user : state.users) {
            if (user.getServerIP().equals(serverIP)) {
                return user;
            }
        }
        return null;
    }

    public boolean removeUserByServerIP(String serverIP) {
        return state.users.removeIf(user -> user.getServerIP().equals(serverIP));
    }

    public void removeAll() {
        state.users.clear();
    }

    public boolean removeUser(User user) {
        return state.users.remove(user);
    }
}