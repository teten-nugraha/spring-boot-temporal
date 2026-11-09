package id.ten.mpv_approval.config;

import id.ten.mpv_approval.entity.UserEntity;
import id.ten.mpv_approval.model.Role;
import id.ten.mpv_approval.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDatabase(UserRepository userRepository) {
        return args -> {
            userRepository.saveAll(List.of(
                    new UserEntity("USR-EMP1", "Budi Santoso", "budi@office.com", Role.EMPLOYEE),
                    new UserEntity("USR-EMP2", "Siti Rahma", "siti@office.com", Role.EMPLOYEE),
                    new UserEntity("USR-MGR1", "Pak Toni (Manager 1)", "toni.mgr1@office.com", Role.MANAGER),
                    new UserEntity("USR-MGR2", "Pak Agus (Manager 2)", "agus.mgr2@office.com", Role.MANAGER),
                    new UserEntity("USR-FIN1", "Bu Lisa (Finance 1)", "lisa.fin1@office.com", Role.FINANCE),
                    new UserEntity("USR-FIN2", "Bu Maya (Finance 2)", "maya.fin2@office.com", Role.FINANCE),
                    new UserEntity("USR-DIR1", "Pak Hartono (Director 1)", "hartono.dir1@office.com", Role.DIRECTOR),
                    new UserEntity("USR-DIR2", "Pak Bambang (Director 2)", "bambang.dir2@office.com", Role.DIRECTOR),
                    new UserEntity("USR-CEO1", "Pak Hendra (CEO 1)", "hendra.ceo1@office.com", Role.CEO),
                    new UserEntity("USR-CEO2", "Pak Rudi (CEO 2)", "rudi.ceo2@office.com", Role.CEO)
            ));
        };
    }
}