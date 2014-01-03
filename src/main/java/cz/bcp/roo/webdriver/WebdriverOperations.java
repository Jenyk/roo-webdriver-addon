package cz.bcp.roo.webdriver;

import org.springframework.roo.model.JavaType;

/**
 * Interface of operations this add-on offers. Typically used by a command type or an external add-on.
 *
 * @since 1.1
 */
public interface WebdriverOperations {

    /**
     * Indicate commands should be available
     * 
     * @return true if it should be available, otherwise false
     */
    boolean isCommandAvailable();

    /**
     * Create WebDriver test for given entity
     */
    void createTestClass(JavaType entity);    
    
    /**
     * Setup all add-on artifacts (dependencies in this case)
     */
    void addDependencies();
}