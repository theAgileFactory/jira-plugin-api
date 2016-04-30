package com.agifac.lib.jira.plugin.api.services;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import com.agifac.lib.jira.plugin.api.services.JiraPluginServiceProvider.BizDockRequirementsFields;

/**
 * The object which stores plugin configuration.<br/>
 * <ul>
 * <li>needsJqlQueryTemplate : the JQL query template to be used to retrieve the
 * issues which are considered as "needs".<br/>
 * ${project} is to be dynamically replaced by the project key</li>
 * <li>defectsJqlQueryTemplate : the JQL query template to be used to retrieve
 * the issues which are considered as "defects".<br/>
 * ${project} is to be dynamically replaced by the project key</li>
 * <li>mappingBizDockJira : the definition of the mapping bewtween the BizDock
 * fields and some JIRA fields</li>
 * </ul>
 * 
 * @author Pierre-Yves Cloux
 */
public class JiraPluginConfiguration implements Serializable {
    public static final String DEFAULT_NEEDS_JQL_QUERY_TEMPLATE = "project = ${" + JiraPluginServiceProvider.PROJECT_TAG_TO_BE_REPLACED
            + "} AND issuetype in (Epic, Improvement, \"New Feature\")";
    public static final String DEFAULT_DEFECTS_JQL_QUERY_TEMPLATE = "project = ${" + JiraPluginServiceProvider.PROJECT_TAG_TO_BE_REPLACED
            + "} AND issuetype = Bug";

    private static final long serialVersionUID = 9050761512620299300L;

    private String needsJqlQueryTemplate = DEFAULT_NEEDS_JQL_QUERY_TEMPLATE;
    private String defectsJqlQueryTemplate = DEFAULT_DEFECTS_JQL_QUERY_TEMPLATE;
    private Map<BizDockRequirementsFields, String> mappingBizDockJira;
    private String userForProjectCreation;

    public JiraPluginConfiguration() {
        this.mappingBizDockJira = Collections.synchronizedMap(new TreeMap<BizDockRequirementsFields, String>());
        // Initialize with the default fields
        for (BizDockRequirementsFields requirementsField : BizDockRequirementsFields.values()) {
            this.mappingBizDockJira.put(requirementsField, requirementsField.getDefaultJiraField());
        }
    }

    public String getNeedsJqlQueryTemplate() {
        return needsJqlQueryTemplate;
    }

    public void setNeedsJqlQueryTemplate(String needsJqlQueryTemplate) {
        this.needsJqlQueryTemplate = needsJqlQueryTemplate;
    }

    public String getDefectsJqlQueryTemplate() {
        return defectsJqlQueryTemplate;
    }

    public void setDefectsJqlQueryTemplate(String defectsJqlQueryTemplate) {
        this.defectsJqlQueryTemplate = defectsJqlQueryTemplate;
    }

    public Map<BizDockRequirementsFields, String> getMappingBizDockJira() {
        return mappingBizDockJira;
    }

    /**
     * Return a string representation of the BizDock JIRA fields mapping
     * 
     * @return
     */
    public String getStringRepresentationOfMappingBizDockJira() {
        Properties properties = new Properties();
        for (BizDockRequirementsFields requirementsField : getMappingBizDockJira().keySet()) {
            properties.setProperty(requirementsField.name(), getMappingBizDockJira().get(requirementsField));
        }
        StringWriter sw = new StringWriter();
        try {
            properties.store(sw, "version " + (new Date()));
        } catch (IOException e) {
            throw new RuntimeException("Unable to serialize the MappingBizDockJira", e);
        }
        return sw.toString();
    }

    /**
     * Load the string representation of the BizDock JIRA fields mapping
     * 
     * @param serializedMapping
     *            the string representation of a MappingBizDockJira
     * @return
     */
    public void setMappingBizDockJira(String serializedMapping) {
        Properties properties = new Properties();
        StringReader sr = new StringReader(serializedMapping);
        try {
            properties.load(sr);
        } catch (IOException e) {
            throw new RuntimeException("Unable to de-serialize the MappingBizDockJira", e);
        }
        for (BizDockRequirementsFields requirementsField : getMappingBizDockJira().keySet()) {
            if (properties.containsKey(requirementsField.name()) && !StringUtils.isBlank(properties.getProperty(requirementsField.name()))) {
                mappingBizDockJira.put(requirementsField, properties.getProperty(requirementsField.name()));
            }
        }
    }

    public String getUserForProjectCreation() {
        return userForProjectCreation;
    }

    public void setUserForProjectCreation(String userForProjectCreation) {
        this.userForProjectCreation = userForProjectCreation;
    }

}