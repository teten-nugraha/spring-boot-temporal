package id.ten.mpv_approval.model;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DummyData {
    public static final Map<String, User> USERS = new HashMap<>();

    static {
        USERS.put("USR-EMP", new User("USR-EMP", "Budi Santoso", "budi@office.com", Role.EMPLOYEE));
        USERS.put("USR-MGR", new User("USR-MGR", "Pak Toni", "toni.manager@office.com", Role.MANAGER));
        USERS.put("USR-FIN", new User("USR-FIN", "Bu Lisa", "lisa.finance@office.com", Role.FINANCE));
        USERS.put("USR-DIR", new User("USR-DIR", "Pak Hartono", "hartono.director@office.com", Role.DIRECTOR));
        USERS.put("USR-CEO", new User("USR-CEO", "Pak Hendra", "hendra.ceo@office.com", Role.CEO));
    }

    public static User findById(String id) {
        return USERS.get(id);
    }
}
