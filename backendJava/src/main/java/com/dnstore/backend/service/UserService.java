package com.dnstore.backend.service;

import com.dnstore.backend.exception.ConflictException;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.repository.StockEntryRepository;
import com.dnstore.backend.repository.StockMovementRepository;
import com.dnstore.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final StockEntryRepository stockEntryRepository;
    private final StockMovementRepository stockMovementRepository;

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public Optional<User> updateRole(UUID id, Role newRole) {
        return userRepository.findById(id).map(user -> {
            user.setRole(newRole);
            return userRepository.save(user);
        });
    }

    public boolean delete(UUID id) {
        if (userRepository.existsById(id)) {
            // O livro de estoque e as notas de entrada referenciam o usuário (FK) e são histórico: o banco
            // impediria o DELETE. Responde 409 com mensagem clara em vez da genérica de integridade.
            if (stockEntryRepository.existsByCreatedBy_Id(id) || stockMovementRepository.existsByActor_Id(id)) {
                throw new ConflictException("Este usuário não pode ser excluído porque tem notas de entrada, "
                        + "ajustes ou movimentações de estoque registrados. Para remover o acesso, altere o papel "
                        + "do usuário em vez de excluí-lo.");
            }
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
