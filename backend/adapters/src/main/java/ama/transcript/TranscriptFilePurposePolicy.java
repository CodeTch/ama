package ama.transcript;
import ama.contract.IdentityContracts.Actor;
import ama.storage.FilePurposePolicy;
/** Explicit installed B profile. Current owner/lifecycle authorization stays in B03/B04. */
public final class TranscriptFilePurposePolicy implements FilePurposePolicy {
 public void require(Actor actor,String meeting,String purpose){if(!JdbcTranscriptAuthority.PURPOSE.equals(purpose)&&!MaterialFileReferencePolicy.PURPOSE.equals(purpose))throw new Unavailable();}
}
