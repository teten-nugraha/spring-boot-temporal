package id.ten.mpv_approval.model;

public record User(
        String id,
        String name,
        String email,
        Role role
) {
}
