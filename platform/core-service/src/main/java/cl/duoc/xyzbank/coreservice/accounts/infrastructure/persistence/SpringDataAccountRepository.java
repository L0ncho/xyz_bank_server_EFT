package cl.duoc.xyzbank.coreservice.accounts.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {
    List<AccountJpaEntity> findByCustomerId(UUID customerId);

    Optional<AccountJpaEntity> findByAccountNumber(String accountNumber);
}
