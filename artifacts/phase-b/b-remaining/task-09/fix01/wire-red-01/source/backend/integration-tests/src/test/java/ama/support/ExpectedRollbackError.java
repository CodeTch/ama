package ama.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.springframework.transaction.support.TransactionTemplate;

/** Captures only the expected rollback log for the current killed-connection test. */
public final class ExpectedRollbackError extends TurboFilter implements AutoCloseable {
    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final Thread owner = Thread.currentThread();
    private final List<Throwable> failures = new ArrayList<>();

    public ExpectedRollbackError() {
        start();
        context.addTurboFilter(this);
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String message,
                              Object[] parameters, Throwable failure) {
        if (Thread.currentThread() == owner && logger.getName().equals(TransactionTemplate.class.getName())
                && level == Level.ERROR
                && "Application exception overridden by rollback exception".equals(message)
                && failure instanceof IllegalStateException && failure.getCause() instanceof SQLException) {
            failures.add(failure);
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }

    public List<Throwable> failures() {
        return List.copyOf(failures);
    }

    @Override
    public void close() {
        context.getTurboFilterList().remove(this);
        stop();
    }
}
