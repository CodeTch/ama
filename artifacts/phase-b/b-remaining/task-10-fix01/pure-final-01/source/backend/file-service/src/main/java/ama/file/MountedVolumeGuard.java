package ama.file;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Supplier;

/** Mount identity and lifecycle checks supplement, but never establish, cluster fencing. */
public final class MountedVolumeGuard implements VolumeGuard {
    public record Config(
            Path root,
            Path mountpoint,
            String device,
            String filesystem,
            String source,
            String volumeId,
            Path roleFile,
            String activeRole) {
        public Config {
            if (root == null
                    || mountpoint == null
                    || !root.isAbsolute()
                    || !mountpoint.isAbsolute()
                    || !root.normalize().equals(root)
                    || !mountpoint.normalize().equals(mountpoint)
                    || !root.startsWith(mountpoint)
                    || device == null
                    || !device.matches("[0-9]+:[0-9]+")
                    || filesystem == null
                    || filesystem.isBlank()
                    || source == null
                    || source.isBlank()
                    || volumeId == null
                    || volumeId.isBlank()
                    || roleFile == null
                    || !roleFile.isAbsolute()
                    || activeRole == null
                    || activeRole.isBlank())
                throw new IllegalArgumentException("mounted-volume configuration required");
        }
    }

    private final Config config;
    private final Supplier<String> mountinfo;

    public MountedVolumeGuard(Config config) {
        this(config, () -> readKernel(Path.of("/proc/self/mountinfo")));
    }

    /** Injectable kernel observation; never bound to any runtime bypass setting. */
    public MountedVolumeGuard(Config config, Supplier<String> mountinfo) {
        this.config = Objects.requireNonNull(config);
        this.mountinfo = Objects.requireNonNull(mountinfo);
    }

    @Override
    public void check(LinuxFiles fs, LinuxFiles.Fd root) {
        try {
            checkIdentity(fs, root);
        } catch (RuntimeException e) {
            throw LinuxFiles.unavailable();
        }
    }

    private void checkIdentity(LinuxFiles fs, LinuxFiles.Fd root) {
        try (var configured = fs.root(config.root())) {
            var expected = fs.identity(configured);
            var actualRoot = fs.identity(root);
            if (expected.device() != actualRoot.device() || expected.inode() != actualRoot.inode())
                throw LinuxFiles.unavailable();
        }
        String id = null;
        String actual = readKernel(Path.of("/proc/self/fdinfo/" + root.value()));
        for (String line : actual.split("\n"))
            if (line.startsWith("mnt_id:")) id = line.substring(7).trim();
        boolean valid = false;
        for (String line : mountinfo.get().split("\n")) {
            String[] fields = line.split(" ");
            int separator = Arrays.asList(fields).indexOf("-");
            if (separator < 6 || separator + 3 >= fields.length) continue;
            if (fields[0].equals(id)
                    && unescape(fields[4]).equals(config.mountpoint().toString())
                    && fields[2].equals(config.device())
                    && fields[separator + 1].equals(config.filesystem())
                    && unescape(fields[separator + 2]).equals(config.source())
                    && Arrays.asList(fields[5].split(",")).contains("rw")) valid = true;
        }
        if (!valid) throw LinuxFiles.unavailable();
        String sentinel = fs.control(root, ".volume-id", 4096);
        String role = fs.control(config.roleFile(), 4096);
        if (!sentinel.equals(config.volumeId()) || !role.equals(config.activeRole()))
            throw LinuxFiles.unavailable();
    }

    private static String unescape(String value) {
        return value.replace("\\040", " ")
                .replace("\\011", "\t")
                .replace("\\012", "\n")
                .replace("\\134", "\\");
    }

    private static String readKernel(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw LinuxFiles.unavailable();
        }
    }
}
