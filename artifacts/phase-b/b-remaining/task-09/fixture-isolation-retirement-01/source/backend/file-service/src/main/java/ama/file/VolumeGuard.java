package ama.file;

/** Cluster lifecycle and mounted-volume admission; called before IO and before ACK. */
@FunctionalInterface
public interface VolumeGuard {
    void check(LinuxFiles files, LinuxFiles.Fd root);
}
