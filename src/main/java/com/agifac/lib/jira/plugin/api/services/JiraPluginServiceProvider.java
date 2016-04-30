package com.agifac.lib.jira.plugin.api.services;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import com.atlassian.crowd.embedded.api.User;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.properties.APKeys;
import com.atlassian.jira.config.properties.ApplicationProperties;
import com.atlassian.jira.event.issue.IssueEvent;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.customfields.manager.OptionsManager;
import com.atlassian.jira.issue.customfields.option.Option;
import com.atlassian.jira.issue.customfields.option.Options;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.FieldManager;
import com.atlassian.jira.issue.fields.NavigableField;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.jql.parser.JqlQueryParser;
import com.atlassian.jira.project.AssigneeTypes;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserUtil;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.query.Query;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

/**
 * The class which holds the plugin data services.<br/>
 * The plugin mainly deal the synchronization with the BizDock Requirement
 * object.
 * <ul>
 * <li>Name : is mapped to the JIRA field "Summary"</li>
 * <li>Description : is mapped to the JIRA field "Description"</li>
 * <li>Category : is mapped to a configurable JIRA field (none by default)</li>
 * <li>Status : is mapped to the JIRA field "Status"</li>
 * <li>Priority : is mapped to a configurable JIRA field(default "Priority")</li>
 * <li>Severity : is mapped to a configurable JIRA field (default none)</li>
 * <li>Author : is mapped to a configurable JIRA field (default "Reporter")</li>
 * <li>Story points : is mapped to a configurable JIRA field (none by default -
 * the field must be a number)</li>
 * <li>Estimation : is mapped to a configurable JIRA field (default is
 * "Remaining Estimate" - such default is handled specifically since the content
 * is converted into hours)</li>
 * <li>Iteration : is mapped to a configurable JIRA field (default is
 * "Affects Version/s")</li>
 * <li>In scope : is mapped to a configurable JIRA field (default is none)</li>
 * </ul>
 * 
 * @author Pierre-Yves Cloux
 */
public class JiraPluginServiceProvider implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(JiraPluginServiceProvider.class);

    /**
     * Prefix used in the BizDockJiraFieldMapping (see
     * {@link JiraPluginConfiguration) to identify the custom fields.
     */
    private static String CUSTOM_FIELD_KEY_PREFIX = "#!custom!#";

    /**
     * Project tag to be replaced in JQL query templates
     */
    public static String PROJECT_TAG_TO_BE_REPLACED = "_jiraProjectKey_";

    /**
     * The configuration setting which contains the plugin secret key.<br/>
     * This key is required for BizDock to call the plugin "except" for the
     * "ping" method which is not authenticated.
     */
    private static String PLUGIN_SECRET_KEY_SETTING = "com.agifac.lib.jira.plugin.api.services.config.secret.key";

    /**
     * The setting which contains the JQL to be used to retrieve the Needs
     */
    private static String PLUGIN_NEEDS_JQL_SETTING = "com.agifac.lib.jira.plugin.api.services.config.needs.jql";

    /**
     * The setting which contains the JQL to be used to retrieve the Defects
     */
    private static String PLUGIN_DEFECTS_JQL_SETTING = "com.agifac.lib.jira.plugin.api.services.config.defects.jql";

    /**
     * The setting which contains the mapping between the JIRA issue fields and
     * BizDock {@link Requirement} object
     */
    private static String PLUGIN_FIELDS_MAPPING_SETTING = "com.agifac.lib.jira.plugin.api.services.config.field.mapping";

    /**
     * The setting which contains the use to be used to create a JIRA project
     */
    private static String PLUGIN_USER_FOR_PROJECT_CREATION_SETTING = "com.agifac.lib.jira.plugin.api.services.config.create.project.user";

    /**
     * The enumeration which maps the JIRA fields to the BizDock data structure.<br/>
     * Each item of the enumeration has the following attributes:
     * <ul>
     * <li>defaultJiraField : the JIRA field associated with this field by
     * default ("" means no default)</li>
     * <li>configurable : the default JIRA field can be changed by something (or
     * not)</li>
     * <li>authorizedAlternativeFields : the possible alternative JIRA fields to
     * be used instead of the default. If the alternative field is
     * CUSTOM_FIELD_KEY_PREFIX this means : any custom attribute.</li>
     * </ul>
     * 
     * @author Pierre-Yves Cloux
     */
    public enum BizDockRequirementsFields {
        Name("summary", false), Description("description", false), Category("", true, CUSTOM_FIELD_KEY_PREFIX), Status("status", false), Priority("priority",
                false), Severity("", true, CUSTOM_FIELD_KEY_PREFIX), Author("creator", true, CUSTOM_FIELD_KEY_PREFIX, "reporter", "assignee"), StoryPoints(
                "", true, CUSTOM_FIELD_KEY_PREFIX), Estimation("timeoriginalestimate", true, CUSTOM_FIELD_KEY_PREFIX), InScope("", true,
                CUSTOM_FIELD_KEY_PREFIX);
        private String defaultJiraField;
        private boolean configurable;
        private String[] authorizedAlternativeFields;

        private BizDockRequirementsFields(String defaultJiraField, boolean configurable, String... authorizedAlternativeFields) {
            this.defaultJiraField = defaultJiraField;
            this.configurable = configurable;
            this.authorizedAlternativeFields = authorizedAlternativeFields;
        }

        public String getDefaultJiraField() {
            return defaultJiraField;
        }

        public boolean isConfigurable() {
            return configurable;
        }

        public String[] getAuthorizedAlternativeFields() {
            return authorizedAlternativeFields;
        }
    }

    private final EventPublisher eventPublisher;
    private final ProjectManager projectManager;
    private final FieldManager fieldManager;
    private final CustomFieldManager customFieldManager;
    private final ApplicationProperties applicationProperties;
    private final PluginSettingsFactory pluginSettingsFactory;
    private final SearchService searchService;
    private final JqlQueryParser jqlQueryParser;
    private final UserUtil userUtil;
    private final OptionsManager optionsManager;
    private final ConstantsManager constantsManager;
    private String secretKey;
    private JiraPluginConfiguration pluginConfiguration;
    private VelocityEngine velocityEngine;

    public JiraPluginServiceProvider(ApplicationProperties applicationProperties, EventPublisher eventPublisher, ProjectManager projectManager,
            FieldManager fieldManager, CustomFieldManager customFieldManager, PluginSettingsFactory pluginSettingsFactory, SearchService searchService,
            JqlQueryParser jqlQueryParser, UserUtil userUtil, OptionsManager optionsManager, ConstantsManager constantsManager) {
        this.applicationProperties = applicationProperties;
        this.eventPublisher = eventPublisher;
        this.projectManager = projectManager;
        this.fieldManager = fieldManager;
        this.customFieldManager = customFieldManager;
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.searchService = searchService;
        this.jqlQueryParser = jqlQueryParser;
        this.userUtil = userUtil;
        this.optionsManager = optionsManager;
        this.constantsManager = constantsManager;
    }

    @Override
    public void destroy() throws Exception {
        getEventPublisher().unregister(this);
        log.warn("BizDock JIRA plugin stopped");
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        getEventPublisher().register(this);
        initVelocityEngine();
        log.warn("BizDock JIRA plugin started");
    }

    /**
     * Initialize the Velocity engine which is used by the
     * {@link JiraPluginConfigurationServlet} but also for JQL template
     * management
     * 
     * @throws Exception
     */
    private void initVelocityEngine() throws Exception {
        velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
        velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
        velocityEngine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, "org.apache.velocity.runtime.log.Log4JLogChute");
        velocityEngine.setProperty("runtime.log.logsystem.log4j.logger", "velocity");
        velocityEngine.init();
    }

    @EventListener
    public void onIssueEvent(IssueEvent issueEvent) {
    }

    /**
     * Return true if the project already exists
     * 
     * @param projectKey
     *            a project key (this is different from RefId)
     * @return
     * @throws JiraPluginException
     */
    public boolean isProjectWithKeyExists(String projectKey) throws JiraPluginException {
        try {
            Project p = getProjectManager().getProjectObjByKey(projectKey);
            return p != null;
        } catch (Exception e) {
            String message = "Error while testing project with this key " + projectKey;
            log.error(message, e);
            throw new JiraPluginException(message, e);
        }
    }

    /**
     * Creates a new JIRA project using the specified name, key and description
     * 
     * @param projectName
     *            the project name
     * @param projectKey
     *            the project key
     * @param projectDescription
     *            the project description
     * @return the unique and stable Id for this project
     * @throws JiraPluginException
     */
    public String createProject(String projectName, String projectKey, String projectDescription) throws JiraPluginException {
        try {
            Project p = getProjectManager().createProject(projectName, projectKey, projectDescription, getPluginConfiguration().getUserForProjectCreation(),
                    null, AssigneeTypes.UNASSIGNED);
            ComponentAccessor.getPermissionSchemeManager().addDefaultSchemeToProject(p);
            ComponentAccessor.getIssueTypeScreenSchemeManager().addSchemeAssociation(p,
                    ComponentAccessor.getIssueTypeScreenSchemeManager().getDefaultScheme());
            log.info("Project successfuly created " + projectKey);
            return String.valueOf(p.getId());
        } catch (Exception e) {
            String message = "Error while creating a project " + projectKey;
            log.error(message, e);
            throw new JiraPluginException(message, e);
        }
    }

    /**
     * Return the list of the project names managed by the JIRA server
     * 
     * @return a list of String (the name of the projects)
     */
    public List<String> getAllProjectNames() {
        List<String> projectNames = new ArrayList<String>();
        List<Project> projects = getProjectManager().getProjectObjects();
        if (projects != null) {
            for (Project project : projects) {
                projectNames.add(project.getName());
            }
        }
        Collections.sort(projectNames);
        return projectNames;
    }

    /**
     * Return the list of the projects managed by the JIRA server
     * 
     * @return a list of Projects
     */
    public List<Project> getAllProjects() {
        return getProjectManager().getProjectObjects();
    }

    /**
     * Return all the defects for the specified project
     * 
     * @param projectRefId
     *            a project unique id
     * @param parameters
     *            some parameters to be used to generate a JQL from the JQL
     *            template
     * @return
     * @throws JiraPluginException
     */
    public List<Requirement> getDefectsForProject(String projectRefId, Map<String, Object> parameters) throws JiraPluginException {
        try {
            String jql = createJqlFromTemplate(getPluginConfiguration().getDefectsJqlQueryTemplate(), projectRefId, parameters);
            return getRequirementsWith(jql, true);
        } catch (Exception e) {
            throw new JiraPluginException("Unable to retreive the defects for project " + projectRefId, e);
        }
    }

    /**
     * Return all the needs for the specified project
     * 
     * @param projectRefId
     *            a project unique id
     * @param parameters
     *            some parameters to be used to generate a JQL from the JQL
     *            template
     * @return
     * @throws JiraPluginException
     */
    public List<Requirement> getNeedsForProject(String projectRefId, Map<String, Object> parameters) throws JiraPluginException {
        try {
            String jql = createJqlFromTemplate(getPluginConfiguration().getNeedsJqlQueryTemplate(), projectRefId, parameters);
            return getRequirementsWith(jql, false);
        } catch (Exception e) {
            throw new JiraPluginException("Unable to retreive the needs for project " + projectRefId, e);
        }
    }

    /**
     * Creates a JQL query from the specified template using the specified
     * project key and the provided parameters
     * 
     * @param jqlTemplate
     *            a JQL query template
     * @param projectRefId
     *            a JIRA project unique id
     * @param parameters
     *            some parameters
     * @return a String which is a JQL query
     * @throws ParseErrorException
     * @throws MethodInvocationException
     * @throws ResourceNotFoundException
     * @throws IOException
     * @throws JiraPluginException
     */
    private String createJqlFromTemplate(String jqlTemplate, String projectRefId, Map<String, Object> parameters) throws ParseErrorException,
            MethodInvocationException, ResourceNotFoundException, IOException, JiraPluginException {
        VelocityContext context = parameters != null ? new VelocityContext(parameters) : new VelocityContext();
        context.put(PROJECT_TAG_TO_BE_REPLACED, getProjectFromId(Long.valueOf(projectRefId)).getKey());
        StringWriter sw = new StringWriter();
        if (!getVelocityEngine().evaluate(context, sw, "JQL from template", new StringReader(jqlTemplate))) {
            throw new JiraPluginException("Error while generating the JQL from " + jqlTemplate + " with projectRefId=" + projectRefId + " and parameters="
                    + parameters);
        }
        return sw.toString();
    }

    /**
     * Return a list of requirements with the specified jql
     * 
     * @param jql
     *            a JQP query
     * @param defect
     *            true if the requirement is a defect
     * @return a list or requirements
     * @throws JiraPluginException
     */
    private List<Requirement> getRequirementsWith(String jql, boolean defect) throws JiraPluginException {
        List<Requirement> requirements = new ArrayList<Requirement>();
        try {
            Query query = getJqlQueryParser().parseQuery(jql);
            User adminUser = getUserUtil().getJiraAdministrators().iterator().next();
            SearchResults searchResults = getSearchService().search(adminUser, query, PagerFilter.getUnlimitedFilter());
            List<Issue> issues = searchResults.getIssues();
            if (issues != null) {
                for (int recordCount = 0; recordCount < issues.size(); recordCount++) {
                    Issue anIssue = issues.get(recordCount);
                    Requirement requirement = createRequirementFromIssue(anIssue);
                    requirement.setDefect(defect);
                    requirements.add(requirement);
                }
            }
        } catch (Exception e) {
            throw new JiraPluginException("Error while retrieving the requirements with JQL " + jql, e);
        }
        return requirements;
    }

    /**
     * Create a requirements using the provided issue (as well as the configured
     * mapping)
     * 
     * @param anIssue
     *            a JIRA issue
     * @return
     * @throws JiraPluginConfigurationException
     */
    private Requirement createRequirementFromIssue(Issue anIssue) throws JiraPluginConfigurationException {
        Requirement requirement = new Requirement();
        for (BizDockRequirementsFields bizDockRequirementsFields : getPluginConfiguration().getMappingBizDockJira().keySet()) {
            String fieldKey = getPluginConfiguration().getMappingBizDockJira().get(bizDockRequirementsFields);
            Object value = null;
            if (fieldKey.startsWith(CUSTOM_FIELD_KEY_PREFIX)) {
                value = anIssue.getCustomFieldValue(getCustomFieldManager().getCustomFieldObject(StringUtils.removeStart(fieldKey, CUSTOM_FIELD_KEY_PREFIX)));
            } else {
                if (fieldKey.equals("summary")) {
                    value = anIssue.getSummary();
                }
                if (fieldKey.equals("description")) {
                    value = anIssue.getDescription();
                }
                if (fieldKey.equals("status")) {
                    value = anIssue.getStatusObject().getName();
                }
                if (fieldKey.equals("priority")) {
                    value = anIssue.getPriorityObject().getName();
                }
                if (fieldKey.equals("creator")) {
                    value = anIssue.getCreator();
                }
                if (fieldKey.equals("reporter")) {
                    value = anIssue.getReporter();
                }
                if (fieldKey.equals("assignee")) {
                    value = anIssue.getAssignee();
                }
                if (fieldKey.equals("timeoriginalestimate")) {
                    // The value is expressed in "seconds" > converts in hours
                    if (anIssue.getOriginalEstimate() != null) {
                        value = anIssue.getOriginalEstimate() / 3600;
                    } else {
                        value = 0;
                    }
                }
            }

            requirement.setId(anIssue.getKey());
            switch (bizDockRequirementsFields) {
            case Author:
                if (value instanceof User) {
                    requirement.setAuthorEmail(((User) value).getEmailAddress());
                } else {
                    requirement.setAuthorEmail(value != null ? String.valueOf(value) : null);
                }
                break;
            case Category:
                requirement.setCategory(value != null ? String.valueOf(value) : null);
                break;
            case Description:
                requirement.setDescription(value != null ? String.valueOf(value) : null);
                break;
            case Estimation:
                try {
                    if (value != null) {
                        Double doubleValue = Double.parseDouble(String.valueOf(value));
                        requirement.setEstimation(doubleValue.longValue());
                    }
                } catch (Exception e) {
                    log.error("Error with the field " + BizDockRequirementsFields.Estimation.name() + " the mapped field is probably not a long", e);
                }
                break;
            case InScope:
                try {
                    requirement.setInScope(Boolean.parseBoolean(String.valueOf(value)));
                } catch (Exception e) {
                }
                break;
            case Name:
                requirement.setName(value != null ? String.valueOf(value) : null);
                break;
            case Priority:
                requirement.setPriority(value != null ? String.valueOf(value) : null);
                break;
            case Severity:
                requirement.setSeverity(value != null ? String.valueOf(value) : null);
                break;
            case Status:
                requirement.setStatus(value != null ? String.valueOf(value) : null);
                break;
            case StoryPoints:
                try {
                    if (value != null) {
                        Double doubleValue = Double.parseDouble(String.valueOf(value));
                        requirement.setStoryPoints(doubleValue.intValue());
                    }
                } catch (Exception e) {
                    log.error("Error with the field " + BizDockRequirementsFields.StoryPoints.name() + " the mapped field is probably not an int", e);
                }
                break;
            default:
                break;

            }
        }
        return requirement;
    }

    /**
     * Return the project associated with the specified key
     * 
     * @param projectRefIdAsLong
     *            a project unique id
     * @return a project
     */
    public Project getProjectFromId(Long projectRefIdAsLong) {
        return getProjectManager().getProjectObj(projectRefIdAsLong);
    }

    /**
     * Return the base URL of the JIRA installation
     * 
     * @return
     */
    public String getBaseUrl() {
        return getApplicationProperties().getString(APKeys.JIRA_BASEURL);
    }

    /**
     * Return some information about the current JIRA instance
     * 
     * @throws JiraPluginException
     */
    public JiraInstanceInfo getJiraInstanceInfo() throws JiraPluginException {
        JiraInstanceInfo jiraInstanceInfo = new JiraInstanceInfo();
        try {
            // Possible values for status
            List<String> possibleValues = new ArrayList<String>();
            Collection<Status> statuses = getConstantsManager().getStatusObjects();
            if (statuses != null) {
                for (Status status : statuses) {
                    possibleValues.add(status.getName());
                }
            }
            jiraInstanceInfo.setJiraStatuses(possibleValues);

            // Possible values for priorities
            possibleValues = new ArrayList<String>();
            Collection<Priority> priorities = getConstantsManager().getPriorityObjects();
            if (priorities != null) {
                for (Priority priority : priorities) {
                    possibleValues.add(priority.getName());
                }
            }
            jiraInstanceInfo.setJiraPriorities(possibleValues);

            // Possible values for severity
            possibleValues = new ArrayList<String>();
            fillPossibleValuesForCustomField(BizDockRequirementsFields.Severity, possibleValues);
            jiraInstanceInfo.setJiraSeverities(possibleValues);

            // All possible JIRA fields
            jiraInstanceInfo.setAllPossibleFields(getAllJiraFields());
        } catch (Exception e) {
            log.error("Error while reading JIRA instance related information", e);
        }
        return jiraInstanceInfo;
    }

    /**
     * Return the possible options for a custom field
     * 
     * @param possibleValues
     *            an array to be filled with the possible values
     * @throws JiraPluginConfigurationException
     */
    private void fillPossibleValuesForCustomField(BizDockRequirementsFields requirementsField, List<String> possibleValues)
            throws JiraPluginConfigurationException {
        if (!getPluginConfiguration().getMappingBizDockJira().get(requirementsField).startsWith(CUSTOM_FIELD_KEY_PREFIX)) {
            // The field is not set to a custom field, probably not a multi
            // value field
            return;
        }
        String fieldKey = StringUtils.removeStart(getPluginConfiguration().getMappingBizDockJira().get(requirementsField), CUSTOM_FIELD_KEY_PREFIX);
        CustomField customField = getCustomFieldManager().getCustomFieldObject(fieldKey);
        Options options = getOptionsManager().getOptions(customField.getConfigurationSchemes().listIterator().next().getOneAndOnlyConfig());
        Iterator<Option> optionsIterator = options.iterator();
        while (optionsIterator.hasNext()) {
            possibleValues.add(optionsIterator.next().getValue());
        }
    }

    /**
     * Return the list of all the fields defined in the system.<br/>
     * Custom and not custom.
     * 
     * @throws JiraPluginException
     */
    private Map<String, String> getAllJiraFields() throws JiraPluginException {
        Map<String, String> jiraFields = new TreeMap<String, String>();
        try {
            Set<NavigableField> navigableFields = getFieldManager().getAllAvailableNavigableFields();
            for (NavigableField navigableField : navigableFields) {
                jiraFields.put(navigableField.getId(), navigableField.getName());
            }
        } catch (Exception e) {
            throw new JiraPluginException("Error while reading all the available JIRA fields", e);
        }
        return jiraFields;
    }

    /**
     * Return all the admin users for this JIRA instance
     * 
     * @return
     */
    public Collection<User> getAllAdminUsers() {
        return getUserUtil().getJiraAdministrators();
    }

    /**
     * Return all the JIRA fields available for an alternative mapping for the
     * specified BizDock field
     * 
     * @param bizDockRequirementsFields
     *            a BizDock field specification
     * @return a map ([key of the field], [name of the field])
     * @throws JiraPluginException
     */
    public Map<String, String> getAllJiraFieldsFor(BizDockRequirementsFields bizDockRequirementsFields) throws JiraPluginException {
        Map<String, String> alternativeFieldsList = new TreeMap<String, String>();
        try {
            String[] possibleAlternativeFieldKeys = bizDockRequirementsFields.getAuthorizedAlternativeFields() != null ? bizDockRequirementsFields
                    .getAuthorizedAlternativeFields() : new String[] {};
            if (!StringUtils.isBlank(bizDockRequirementsFields.getDefaultJiraField())) {
                possibleAlternativeFieldKeys = ArrayUtils.addAll(bizDockRequirementsFields.getAuthorizedAlternativeFields(),
                        new String[] { bizDockRequirementsFields.getDefaultJiraField() });
            }
            for (String alternativeFieldKey : possibleAlternativeFieldKeys) {
                if (alternativeFieldKey.equals(CUSTOM_FIELD_KEY_PREFIX)) {
                    // This means "add all the possible custom fields"
                    List<CustomField> customFields = getCustomFieldManager().getCustomFieldObjects();
                    if (customFields != null) {
                        for (CustomField customField : customFields) {
                            alternativeFieldsList.put(CUSTOM_FIELD_KEY_PREFIX + customField.getId(), customField.getName());
                        }
                    }
                } else {
                    if (alternativeFieldKey.startsWith(CUSTOM_FIELD_KEY_PREFIX)) {
                        // This is a custom field
                        CustomField customField = getCustomFieldManager().getCustomFieldObject(
                                StringUtils.removeStart(alternativeFieldKey, CUSTOM_FIELD_KEY_PREFIX));
                        alternativeFieldsList.put(CUSTOM_FIELD_KEY_PREFIX + customField.getId(), customField.getName());
                    } else {
                        // This is a standard field
                        NavigableField navigableField = getFieldManager().getNavigableField(alternativeFieldKey);
                        alternativeFieldsList.put(navigableField.getId(), navigableField.getName());
                    }
                }
            }
        } catch (Exception e) {
            throw new JiraPluginException("Error while reading all the available JIRA fields for " + bizDockRequirementsFields, e);
        }
        return alternativeFieldsList;
    }

    /**
     * Reset the configuration to the default (in case of plugin inconsistency)
     * 
     * @throws JiraPluginConfigurationException
     */
    public synchronized void resetConfiguration() throws JiraPluginConfigurationException {
        try {
            PluginSettings pluginSettings = getPluginSettingsFactory().createGlobalSettings();
            JiraPluginConfiguration tmp = new JiraPluginConfiguration();
            pluginSettings.put(PLUGIN_NEEDS_JQL_SETTING, tmp.getNeedsJqlQueryTemplate());
            pluginSettings.put(PLUGIN_DEFECTS_JQL_SETTING, tmp.getDefectsJqlQueryTemplate());
            pluginSettings.put(PLUGIN_FIELDS_MAPPING_SETTING, tmp.getStringRepresentationOfMappingBizDockJira());
            User adminUser = getUserUtil().getJiraAdministrators().iterator().next();
            pluginSettings.put(PLUGIN_USER_FOR_PROJECT_CREATION_SETTING, adminUser.getName());
            pluginConfiguration.setUserForProjectCreation(adminUser.getName());
            this.pluginConfiguration = tmp;
        } catch (Exception e) {
            throw new JiraPluginConfigurationException("Error while reseting the plugin configuration", e);
        }
    }

    /**
     * Return the configuration associated with this plugin
     * 
     * @return
     * @throws JiraPluginException
     */
    public synchronized JiraPluginConfiguration getPluginConfiguration() throws JiraPluginConfigurationException {
        if (pluginConfiguration == null) {
            try {
                PluginSettings pluginSettings = getPluginSettingsFactory().createGlobalSettings();
                pluginConfiguration = new JiraPluginConfiguration();
                if (pluginSettings.get(PLUGIN_NEEDS_JQL_SETTING) != null) {
                    pluginConfiguration.setNeedsJqlQueryTemplate((String) pluginSettings.get(PLUGIN_NEEDS_JQL_SETTING));
                }
                if (pluginSettings.get(PLUGIN_DEFECTS_JQL_SETTING) != null) {
                    pluginConfiguration.setDefectsJqlQueryTemplate((String) pluginSettings.get(PLUGIN_DEFECTS_JQL_SETTING));
                }
                if (pluginSettings.get(PLUGIN_FIELDS_MAPPING_SETTING) != null) {
                    pluginConfiguration.setMappingBizDockJira((String) pluginSettings.get(PLUGIN_FIELDS_MAPPING_SETTING));
                }
                if (pluginSettings.get(PLUGIN_USER_FOR_PROJECT_CREATION_SETTING) != null) {
                    pluginConfiguration.setUserForProjectCreation((String) pluginSettings.get(PLUGIN_USER_FOR_PROJECT_CREATION_SETTING));
                } else {
                    User adminUser = getUserUtil().getJiraAdministrators().iterator().next();
                    pluginConfiguration.setUserForProjectCreation(adminUser.getName());
                }
                return pluginConfiguration;
            } catch (Exception e) {
                throw new JiraPluginConfigurationException("Error while initializing the plugin configuration", e);
            }
        }
        return pluginConfiguration;
    }

    /**
     * Update user for project creation
     * 
     * @param userName
     *            a user name to be used for project creation
     * @throws JiraPluginException
     */
    public synchronized void updateUserForProjectCreation(String userName) throws JiraPluginException {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Updating user for project creation " + userName);
            }
            PluginSettings pluginSettings = getPluginSettingsFactory().createGlobalSettings();
            ApplicationUser user = getUserUtil().getUserByName(userName);
            boolean isAdmin = false;
            for (User userAdmin : getUserUtil().getJiraAdministrators()) {
                if (userAdmin.getName().equals(user.getName())) {
                    isAdmin = true;
                }
            }
            if (user != null && user.isActive() && isAdmin) {
                pluginSettings.put(PLUGIN_USER_FOR_PROJECT_CREATION_SETTING, userName);
                this.pluginConfiguration.setUserForProjectCreation(userName);
            } else {
                throw new JiraPluginException("Unknow or invalid user " + userName);
            }
        } catch (Exception e) {
            String message = "Unable to update the project creation user : " + userName;
            log.error(message, e);
            throw new JiraPluginException(message, e);
        }
    }

    /**
     * Update the mapping between BizDock and Jira (see {@link Requirement})
     * 
     * @param mappingBizDockJiraUpdate
     *            an updated mapping for some fields
     */
    public synchronized void updatePluginConfigurationMapping(Map<BizDockRequirementsFields, String> mappingBizDockJiraUpdate) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Updating requirements mapping with " + mappingBizDockJiraUpdate);
            }
            PluginSettings pluginSettings = getPluginSettingsFactory().createGlobalSettings();
            for (BizDockRequirementsFields bizDockRequirementsFields : mappingBizDockJiraUpdate.keySet()) {
                if (bizDockRequirementsFields.isConfigurable()) {
                    pluginConfiguration.getMappingBizDockJira().put(bizDockRequirementsFields, mappingBizDockJiraUpdate.get(bizDockRequirementsFields));
                }
            }
            pluginSettings.put(PLUGIN_FIELDS_MAPPING_SETTING, pluginConfiguration.getStringRepresentationOfMappingBizDockJira());
        } catch (Exception e) {
            log.error("Unable to update the BizDock Jira mapping", e);
        }
    }

    /**
     * Update the filtering rules for the "Needs"
     * 
     * @param needsJqlQueryTemplate
     *            a JQL query template
     * @return a Pair ([true if the update was successful],[A message if the
     *         update was NOT successful])
     */
    public synchronized Pair<Boolean, String> updatePluginConfigurationNeedsJqlQueryTemplate(String needsJqlQueryTemplate) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Updating needs JQL template with " + needsJqlQueryTemplate);
            }
            PluginSettings pluginSettings = getPluginSettingsFactory().createGlobalSettings();
            pluginSettings.put(PLUGIN_NEEDS_JQL_SETTING, needsJqlQueryTemplate);
            this.pluginConfiguration.setNeedsJqlQueryTemplate(needsJqlQueryTemplate);
        } catch (Exception e) {
            log.error("Unable to update the JQL template for needs", e);
            return Pair.of(false, "Error : " + e.getMessage());
        }
        return Pair.of(true, null);
    }

    /**
     * Update the filtering rules for the "Defects"
     * 
     * @param defectsJqlQueryTemplate
     *            a JQL query template
     * @return a Pair ([true if the update was successful],[A message if the
     *         update was NOT successful])
     */
    public synchronized Pair<Boolean, String> updatePluginConfigurationDefectsJqlQueryTemplate(String defectsJqlQueryTemplate) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Updating defects JQL template with " + defectsJqlQueryTemplate);
            }
            PluginSettings pluginSettings = getPluginSettingsFactory().createGlobalSettings();
            pluginSettings.put(PLUGIN_DEFECTS_JQL_SETTING, defectsJqlQueryTemplate);
            this.pluginConfiguration.setDefectsJqlQueryTemplate(defectsJqlQueryTemplate);
        } catch (Exception e) {
            log.error("Unable to update the JQL template for defects", e);
            return Pair.of(false, "Error : " + e.getMessage());
        }
        return Pair.of(true, null);
    }

    /**
     * Return the secretkey for this plugin
     * 
     * @return
     * @throws JiraPluginException
     */
    public String getSecretKey() throws JiraPluginException {
        if (secretKey == null) {
            try {
                PluginSettings pluginSettings = getPluginSettingsFactory().createGlobalSettings();
                if (pluginSettings.get(PLUGIN_SECRET_KEY_SETTING) == null) {
                    this.secretKey = computeSecretKey(pluginSettings);
                } else {
                    this.secretKey = (String) pluginSettings.get(PLUGIN_SECRET_KEY_SETTING);
                }
            } catch (Exception e) {
                throw new JiraPluginException("Error while retrieving the secret key", e);
            }
        }
        return secretKey;
    }

    /**
     * Reset the BizDock secret key
     * 
     * @throws JiraPluginException
     */
    public void resetSecretKey() throws JiraPluginException {
        try {
            PluginSettings pluginSettings = getPluginSettingsFactory().createGlobalSettings();
            if (log.isDebugEnabled()) {
                log.debug("Secret key reset request");
            }
            this.secretKey = computeSecretKey(pluginSettings);
        } catch (Exception e) {
            throw new JiraPluginException("Error while reseting the BizDock secret key", e);
        }
    }

    /**
     * Authenticate the request using the provided parameters.<br/>
     * The authentication is based on a hash:<br/>
     * Base64(SHA256([secret key]+"#" + requestUri + "#" + timeStamp))
     * 
     * @param authenticationString
     *            a hashed authentication statement
     * @param timeStamp
     *            a timestamp (long : number of milliseconds since January 1,
     *            1970, 00:00:00 GMT )
     * @param requestUri
     *            the request URI (including the query string if any)
     * @param random
     * @return
     */
    public boolean authenticateClient(String authenticationString, long timeStamp, String requestUri) throws JiraPluginException {
        try {
            String hash = createHash(getSecretKey(), timeStamp, requestUri);
            if (log.isDebugEnabled()) {
                log.debug("Checking hash " + authenticationString + " against " + hash);
                log.debug("Hash created from " + timeStamp + " and " + requestUri);
            }
            return authenticationString.equals(hash);
        } catch (Exception e) {
            throw new JiraPluginException("Error while validating an API signature", e);
        }
    }

    /**
     * Create a has for API authentication
     * 
     * @param secretKey
     *            a secret key
     * @param timeStamp
     *            a timestamp
     * @param requestUri
     *            the request URI
     * @return a hash
     * @throws NoSuchAlgorithmException
     */
    private static String createHash(String secretKey, long timeStamp, String requestUri) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String clearHash = secretKey + "#" + requestUri + "#" + timeStamp;
        return new String(Base64.encodeBase64URLSafe(digest.digest(clearHash.getBytes())));
    }

    public static void main(String[] args) {
        try {
            String secretKey = "OzZfh33S+aesGmmC0bAS0dsnf9YSFCU8AYmzyawIWYM=";
            long timeStamp = System.currentTimeMillis();
            String hash = createHash(secretKey, timeStamp, "/rest/taf_api/1.0/api/projects/create");
            System.out.println("Hash [" + hash + "]");
            System.out.println("Timestamp [" + timeStamp + "]");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Compute a secret key and store it into the plugin settings
     * 
     * @param pluginSettings
     * @param pluginConfiguration
     * @throws NoSuchAlgorithmException
     */
    private String computeSecretKey(PluginSettings pluginSettings) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String secretKey = new String(Base64.encodeBase64(digest.digest(UUID.randomUUID().toString().getBytes())));
        pluginSettings.put(PLUGIN_SECRET_KEY_SETTING, secretKey);
        return secretKey;
    }

    private EventPublisher getEventPublisher() {
        return eventPublisher;
    }

    private ProjectManager getProjectManager() {
        return projectManager;
    }

    private ApplicationProperties getApplicationProperties() {
        return applicationProperties;
    }

    private PluginSettingsFactory getPluginSettingsFactory() {
        return pluginSettingsFactory;
    }

    private FieldManager getFieldManager() {
        return fieldManager;
    }

    private CustomFieldManager getCustomFieldManager() {
        return customFieldManager;
    }

    private SearchService getSearchService() {
        return searchService;
    }

    private JqlQueryParser getJqlQueryParser() {
        return jqlQueryParser;
    }

    private UserUtil getUserUtil() {
        return userUtil;
    }

    private OptionsManager getOptionsManager() {
        return optionsManager;
    }

    private ConstantsManager getConstantsManager() {
        return constantsManager;
    }

    VelocityEngine getVelocityEngine() {
        return velocityEngine;
    }
}
