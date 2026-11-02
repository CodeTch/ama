package ama.identity;
import ama.contract.IdentityContracts.PeoplePage;
import ama.contract.IdentityContracts.Departments;
import ama.contract.IdentityContracts.ProviderUnavailableException;
public interface DirectoryPort {
    PeoplePage search(String query, String cursor, int limit);
    default PeoplePage search(String query, String cursor, int limit, String departmentId) {
        if (departmentId != null) throw new ProviderUnavailableException();
        return search(query, cursor, limit);
    }
    default Departments departments() { throw new ProviderUnavailableException(); }
}
