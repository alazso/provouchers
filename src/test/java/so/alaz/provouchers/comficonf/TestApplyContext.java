package so.alaz.provouchers.comficonf;

import com.comficonf.api.Actor;
import com.comficonf.api.ApplyContext;
import com.comficonf.api.ChangeKind;
import com.comficonf.api.ConfigValues;
import com.comficonf.api.FieldChange;
import com.comficonf.api.ResourceId;
import com.comficonf.api.schema.ApplyMode;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/** An {@link ApplyContext} whose {@code callSync} runs the task inline and counts the calls. */
final class TestApplyContext implements ApplyContext {

    final AtomicInteger syncCalls = new AtomicInteger();
    private final String baseRevision;
    private final ConfigValues current;
    private final ConfigValues proposed;
    private final List<FieldChange> changes;

    TestApplyContext(String baseRevision, ConfigValues current, ConfigValues proposed, List<FieldChange> changes) {
        this.baseRevision = baseRevision;
        this.current = current;
        this.proposed = proposed;
        this.changes = changes;
    }

    /** A context for one modified scalar field, with the before value taken from {@code current}. */
    static TestApplyContext changing(String baseRevision, ConfigValues current, String path, Object value,
                                     ApplyMode mode) {
        Object before = current.get(path);
        FieldChange change = new FieldChange(path, ChangeKind.MODIFIED, mode, false, null, before, value);
        return new TestApplyContext(baseRevision, current, current.with(path, value), List.of(change));
    }

    @Override
    public ResourceId resourceId() {
        return ResourceId.parse(ComfiConfIntegration.RESOURCE_ID);
    }

    @Override
    public String operationId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String baseRevision() {
        return baseRevision;
    }

    @Override
    public ConfigValues current() {
        return current;
    }

    @Override
    public ConfigValues proposed() {
        return proposed;
    }

    @Override
    public List<FieldChange> changes() {
        return changes;
    }

    @Override
    public boolean requiresRestart() {
        return changes.stream().anyMatch(change -> change.applyMode() == ApplyMode.RESTART);
    }

    @Override
    public Actor actor() {
        return new Actor(UUID.randomUUID().toString(), "owner@example.com");
    }

    @Override
    public <T> T callSync(Callable<T> task) throws Exception {
        syncCalls.incrementAndGet();
        return Objects.requireNonNull(task, "task").call();
    }
}
