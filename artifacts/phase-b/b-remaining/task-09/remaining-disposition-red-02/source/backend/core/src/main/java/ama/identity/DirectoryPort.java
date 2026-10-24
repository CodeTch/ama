package ama.identity;
import ama.contract.IdentityContracts.PeoplePage;
public interface DirectoryPort {
    PeoplePage search(String query, String cursor, int limit);
}
