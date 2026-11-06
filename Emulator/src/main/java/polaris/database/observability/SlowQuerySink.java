package polaris.database.observability;

@FunctionalInterface
public interface SlowQuerySink {
    void accept(SlowQueryEvent event);
}
