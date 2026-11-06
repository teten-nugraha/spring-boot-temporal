package id.ten.mpv_approval.repository;

import id.ten.mpv_approval.entity.ApprovalHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistoryEntity, Long> {}