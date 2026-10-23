package ama.storage;
import java.nio.file.*;
final class Task06Resources {
 static final Path ROOT=Path.of(System.getenv("AMA_TASK05_ROOT")).toAbsolutePath().normalize();
 static Path evidencePath()throws Exception {Path p=ROOT.getParent().getParent().resolve("artifacts/phase-b/b-remaining/task-06/native");Files.createDirectories(p);return p;}
}
