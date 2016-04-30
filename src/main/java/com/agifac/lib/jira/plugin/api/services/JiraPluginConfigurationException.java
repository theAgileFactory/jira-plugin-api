package com.agifac.lib.jira.plugin.api.services;

/**
 * The exception which is thrown when a configuration exception is detected.
 * 
 * @author Pierre-Yves Cloux
 */
public class JiraPluginConfigurationException extends JiraPluginException {
    private static final long serialVersionUID = 6137266122057780371L;

    public JiraPluginConfigurationException() {
    }

    public JiraPluginConfigurationException(String message) {
        super(message);
    }

    public JiraPluginConfigurationException(Throwable cause) {
        super(cause);
    }

    public JiraPluginConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public JiraPluginConfigurationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
