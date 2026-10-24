package ama.identity;
import ama.contract.IdentityContracts.Actor;
import java.util.Optional;
public interface IdentityPort {
    Optional<Actor> resolveSession(String token);
}
