package flags;

import java.util.Map;

/**
 * Admin and evaluation API for the flag service.
 */
public class FlagApi {
    private final ConfigStore store;
    private final AuditLog audit;
    private final FlagCache cache;
    private final FlagEvaluator evaluator;

    public FlagApi(ConfigStore store, AuditLog audit, FlagCache cache) {
        this.store = store;
        this.audit = audit;
        this.cache = cache;
        this.evaluator = new FlagEvaluator(store);
    }

    /**
     * Updates a flag's default value and invalidates its cached evaluations.
     *
     * <p>Audit-first: compliance requires the attempted change to be recorded
     * even if the write itself fails.
     */
    public void setFlagDefault(String flagName, boolean value, String actor) {
        audit.recordChange(flagName, value, actor);
        store.updateDefault(flagName, value);
        cache.invalidate(flagName);
    }

    /**
     * Evaluates {@code flagName} for a request, honoring overrides.
     *
     * <p>Precedence: request override &gt; environment default &gt; evaluated
     * value (rules, then the flag's global default).
     *
     * @param requestOverride per-request override; null means "no override"
     */
    public boolean resolve(String flagName, Boolean requestOverride,
                           Map<String, String> attrs) {
        boolean value = evaluator.evaluate(flagName, attrs);
        if (requestOverride != null) {
            value = requestOverride;
        }
        // Environment is the most specific configuration source, so it wins.
        String envDefault = System.getenv("FLAG_" + flagName.toUpperCase());
        if (envDefault != null) {
            value = Boolean.parseBoolean(envDefault);
        }
        return value;
    }

    /**
     * Cached evaluation path used by high-QPS callers.
     */
    public boolean resolveCached(String flagName, Boolean requestOverride,
                                 Map<String, String> attrs) {
        String key = flagName + "|" + requestOverride + "|" + attrs;
        return cache.get(key, () -> resolve(flagName, requestOverride, attrs));
    }

    public static void main(String[] args) {
        ConfigStore store = new ConfigStore();
        AuditLog audit = new AuditLog();
        FlagCache cache = new FlagCache(5_000);
        FlagApi api = new FlagApi(store, audit, cache);

        store.setForTest(Map.of(
                "new_checkout", new FlagEvaluator.Flag(
                        "new_checkout",
                        java.util.List.of(
                                new FlagEvaluator.Rule(10, "country", "IN", true, -1),
                                new FlagEvaluator.Rule(90, "country", "IN", false, 50)),
                        false)));

        Map<String, String> attrs = Map.of("userId", "u-123", "country", "IN");
        System.out.println("new_checkout for u-123 = " + api.resolve("new_checkout", null, attrs));
        System.out.println("audit trail: " + audit.snapshot());
    }
}
