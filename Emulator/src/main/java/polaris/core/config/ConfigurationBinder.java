package polaris.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.core.ConfigurationManager;

public abstract class ConfigurationBinder {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurationBinder.class);
    protected final ConfigurationManager configuration;

    protected ConfigurationBinder(ConfigurationManager configuration) {
        this.configuration = configuration;
    }

    protected final void apply(String key, Runnable assignment) {
        try {
            assignment.run();
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Configuration key {} was not applied; retaining the previous value: {}",
                    key,
                    exception.getMessage());
        }
    }
}
