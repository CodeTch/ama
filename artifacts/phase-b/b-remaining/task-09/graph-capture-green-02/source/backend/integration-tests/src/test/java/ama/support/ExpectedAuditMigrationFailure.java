package ama.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

/** Captures only the known V006 rejection log on the owning negative-test thread. */
public final class ExpectedAuditMigrationFailure extends TurboFilter implements AutoCloseable {
    private static final String EXPECTED_MESSAGE = "Migration of schema `ama_b01_fixture` to version "
            + "\"006 - audit metadata\" failed! Please restore backups and roll back database and code!";
    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final Thread owner = Thread.currentThread();
    private final List<String> messages = new ArrayList<>();

    public ExpectedAuditMigrationFailure() {
        start();
        context.addTurboFilter(this);
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String message,
                              Object[] parameters, Throwable failure) {
        if (Thread.currentThread() == owner
                && logger.getName().equals("org.flywaydb.core.internal.command.DbMigrate")
                && level == Level.ERROR && EXPECTED_MESSAGE.equals(message)
                && (parameters == null || parameters.length == 0) && failure == null) {
            messages.add(message);
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }

    @Override
    public void close() {
        context.getTurboFilterList().remove(this);
        stop();
    }
}
