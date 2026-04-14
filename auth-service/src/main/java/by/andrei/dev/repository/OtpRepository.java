package by.andrei.dev.repository;

import by.andrei.dev.service.OtpCode;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpRepository extends CrudRepository<OtpCode, String> {
    Optional<OtpCode> findByCode(String code);
}
