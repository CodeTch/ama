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

/** Exact expected V007 refusal on the owner test thread; unrelated failures stay visible. */
public final class ExpectedIdentityMigrationFailure extends TurboFilter implements AutoCloseable {
    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final Thread owner = Thread.currentThread();
    private final List<String> messages = new ArrayList<>();
    public ExpectedIdentityMigrationFailure() { start(); context.addTurboFilter(this); }
    @Override public FilterReply decide(Marker marker, Logger logger, Level level, String message, Object[] parameters, Throwable failure) {
        if (Thread.currentThread() == owner && logger.getName().equals("org.flywaydb.core.internal.command.DbMigrate")
                && level == Level.ERROR && (parameters == null || parameters.length == 0) && failure == null
                && "Migration of schema `ama_b01_fixture` to version \"007 - identity sessions\" failed! Please restore backups and roll back database and code!".equals(message)) {
            messages.add(message); return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
    public List<String> messages() { return List.copyOf(messages); }
    @Override public void close() { context.getTurboFilterList().remove(this); stop(); }
}
