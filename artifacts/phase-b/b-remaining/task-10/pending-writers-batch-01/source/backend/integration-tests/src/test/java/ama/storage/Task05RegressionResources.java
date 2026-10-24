package ama.storage;
import java.nio.file.*;import java.util.*;
/** Paths for inherited checks only; originals retain their historical fixture behavior. */
final class Task05RegressionResources {
 static final Path ROOT=Path.of(System.getenv("AMA_TASK05_ROOT")).toAbsolutePath().normalize();
 static Path nativePath()throws Exception {Path parent=ROOT.resolve("native/data");if(Files.isSymbolicLink(parent)||!parent.toRealPath().startsWith(ROOT.toRealPath()))throw new IllegalStateException("custody");Path p=parent.resolve("case-"+UUID.randomUUID());if(Files.exists(p)||Files.isSymbolicLink(p))throw new IllegalStateException("collision");return p;}
 static Path archivePath()throws Exception {Path parent=ROOT.resolve("runtime");if(Files.isSymbolicLink(parent)||!parent.toRealPath().startsWith(ROOT.toRealPath()))throw new IllegalStateException("custody");Path p=Files.createDirectory(parent.resolve("archive-"+UUID.randomUUID()));LocalFixture.privateMode(p,true);return p;}
 static Path evidencePath()throws Exception {Path p=ROOT.getParent().getParent().resolve("artifacts/phase-b/b-remaining/task-05/forward-custody");Files.createDirectories(p);return p;}
}
