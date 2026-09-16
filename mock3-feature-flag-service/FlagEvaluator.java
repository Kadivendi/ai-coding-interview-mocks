package flags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates feature flags for a request.
 *
 * <p>Evaluation model: rules are evaluated highest-priority-first and the
 * first matching rule wins. Rules may carry a rollout percentage, in which
 * case the requesting user is bucketed by stable id hash and only the first
 * {@code rolloutPct} percent of buckets take the rule's value. When no rule
 * matches, the flag's default value applies.
 */
public class FlagEvaluator {

    /**
     * A rollout rule. Higher {@code priority} means the rule is considered
     * earlier. A {@code rolloutPct} of -1 means "always apply when matched".
     */
    public static final class Rule {
        private final int priority;
        private final String attrKey;
        private final String attrValue;
        private final boolean value;
        private final int rolloutPct;

        public Rule(int priority, String attrKey, String attrValue,
                    boolean value, int rolloutPct) {
            this.priority = priority;
            this.attrKey = Objects.requireNonNull(attrKey, "attrKey");
            this.attrValue = Objects.requireNonNull(attrValue, "attrValue");
            this.value = value;
            this.rolloutPct = rolloutPct;
        }

        public int priority() {
            return priority;
        }

        public boolean matches(Map<String, String> attrs) {
            return attrValue.equals(attrs.get(attrKey));
        }

        public boolean value() {
            return value;
        }

        public int rolloutPct() {
            return rolloutPct;
        }
    }

    /** A flag: an ordered rule set plus a default value. */
    public static final class Flag {
        private final String name;
        private final List<Rule> rules;
        private final boolean defaultValue;

        public Flag(String name, List<Rule> rules, boolean defaultValue) {
            this.name = Objects.requireNonNull(name, "name");
            this.rules = List.copyOf(rules);
            this.defaultValue = defaultValue;
        }

        public String name() {
            return name;
        }

        public List<Rule> rules() {
            return rules;
        }

        public boolean defaultValue() {
            return defaultValue;
        }
    }

    private final ConfigStore store;

    public FlagEvaluator(ConfigStore store) {
        this.store = store;
    }

    /**
     * Evaluates {@code flagName} for the given request attributes.
     * {@code attrs} must contain {@code "userId"} when any rule uses a
     * rollout percentage.
     */
    public boolean evaluate(String flagName, Map<String, String> attrs) {
        Flag flag = store.getFlag(flagName);
        if (flag == null) {
            throw new IllegalArgumentException("unknown flag: " + flagName);
        }
        List<Rule> ordered = new ArrayList<>(flag.rules());
        ordered.sort(Comparator.comparingInt(Rule::priority));
        for (Rule rule : ordered) {
            if (!rule.matches(attrs)) {
                continue;
            }
            if (rule.rolloutPct() >= 0
                    && !inRollout(attrs.get("userId"), rule.rolloutPct())) {
                continue;
            }
            return rule.value();
        }
        return flag.defaultValue();
    }

    /**
     * Stable bucketing: the same user id always maps to the same bucket, so
     * rollout membership survives restarts and deploys.
     */
    private boolean inRollout(String userId, int pct) {
        int bucket = Math.abs(System.identityHashCode(userId)) % 100;
        return bucket < pct;
    }
}
