package com.agifac.lib.jira.plugin.api.services;

import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A structure which contains some information regarding the JIRA instance.<br/>
 * This structure is to be used by BizDock to help with the configuration of the
 * BizDock JIRA link.<br/>
 * <ul>
 * <li>jiraStatuses : the possible values for the status field</li>
 * <li>jiraPriorities : the possible values for the priority field</li>
 * <li>jiraSeverities : the possible values for the severity field</li>
 * <li>allPossibleFields : all the JIRA fields defined in the system</li>
 * </ul>
 * 
 * @author Pierre-Yves Cloux
 */
@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class JiraInstanceInfo {
    private List<String> jiraStatuses;
    private List<String> jiraPriorities;
    private List<String> jiraSeverities;
    private Map<String, String> allPossibleFields;

    public JiraInstanceInfo() {
    }

    @XmlElement(name = "statuses")
    public List<String> getJiraStatuses() {
        return jiraStatuses;
    }

    public void setJiraStatuses(List<String> jiraStatuses) {
        this.jiraStatuses = jiraStatuses;
    }

    @XmlElement(name = "priorities")
    public List<String> getJiraPriorities() {
        return jiraPriorities;
    }

    public void setJiraPriorities(List<String> jiraPriorities) {
        this.jiraPriorities = jiraPriorities;
    }

    @XmlElement(name = "severities")
    public List<String> getJiraSeverities() {
        return jiraSeverities;
    }

    public void setJiraSeverities(List<String> jiraSeverities) {
        this.jiraSeverities = jiraSeverities;
    }

    @XmlElement(name = "allPossibleJiraFields")
    public Map<String, String> getAllPossibleFields() {
        return allPossibleFields;
    }

    public void setAllPossibleFields(Map<String, String> allPossibleFields) {
        this.allPossibleFields = allPossibleFields;
    }

}
