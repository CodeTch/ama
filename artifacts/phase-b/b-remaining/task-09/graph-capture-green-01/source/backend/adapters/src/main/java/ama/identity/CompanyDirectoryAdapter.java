package ama.identity;
import ama.contract.IdentityContracts.PeoplePage;
import ama.contract.IdentityContracts.ProviderUnavailableException;
public final class CompanyDirectoryAdapter implements DirectoryPort {
    @Override public PeoplePage search(String query, String cursor, int limit) { throw new ProviderUnavailableException(); }
}
