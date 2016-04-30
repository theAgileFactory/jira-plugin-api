package com.agifac.lib.jira.plugin.api.services;

/**
 * Generic exception
 * 
 * @author Pierre-Yves Cloux
 */
public class JiraPluginException extends Exception {
    private static final long serialVersionUID = -8604005332834314172L;

    public JiraPluginException() {
    }

    public JiraPluginException(String message) {
        super(message);
    }

    public JiraPluginException(Throwable cause) {
        super(cause);
    }

    public JiraPluginException(String message, Throwable cause) {
        super(message, cause);
    }

    public JiraPluginException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
