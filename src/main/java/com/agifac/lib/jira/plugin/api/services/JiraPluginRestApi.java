package com.agifac.lib.jira.plugin.api.services;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agifac.lib.jira.plugin.api.services.JiraPluginRestApi.ErrorResponse.ErrorCode;
import com.atlassian.jira.project.Project;
import com.atlassian.plugins.rest.common.security.AnonymousAllowed;

/**
 * The service which provides REST exposition of some reference data. This REST
 * service is to be used by some of the custom fields (from client side)
 * 
 * @author Pierre-Yves Cloux
 */
@Path("/api")
public class JiraPluginRestApi {
    private static final Logger log = LoggerFactory.getLogger(JiraPluginRestApi.class);

    private static final String AUTHENTICATION_STRING_HEADER = "x-jira-bizdock-auth";
    private static final String TIME_STAMP_HEADER = "x-jira-bizdock-timestamp";

    @Context
    private HttpServletRequest httpRequest;

    private final JiraPluginServiceProvider jiraPluginServiceProvider;

    public JiraPluginRestApi(JiraPluginServiceProvider jiraPluginServiceProvider) {
        this.jiraPluginServiceProvider = jiraPluginServiceProvider;
    }

    /**
     * This method is a test method to be called to check if the plugin is
     * "alive".<br/>
     * It performs various tests to check if the authentication mode can work.<br/>
     * This will enable the BizDock plugin to give relevant information in case
     * of failure.
     * 
     * @return
     */
    @GET
    @AnonymousAllowed
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/ping")
    public Response getPing() {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Ping requested by : " + getApiClientIdentification());
            }
            PingReponse response = new PingReponse();
            response.setMessage("I am alive and I know about the the following projects : " + getJiraPluginServiceProvider().getAllProjectNames());
            response.setRequestURI(getHttpRequest().getRequestURI());
            response.setAuthenticationHeader(getHttpRequest().getHeader(AUTHENTICATION_STRING_HEADER));
            response.setTimeStampHeader(getHttpRequest().getHeader(TIME_STAMP_HEADER));
            boolean authenticated = false;
            try {
                checkAuthentication();
                authenticated = true;
            } catch (Exception exp) {
            }
            response.setAuthenticated(authenticated);
            return Response.ok(response).build();
        } catch (Exception e) {
            return returnErrorResponseOnException(e);
        }
    }

    /**
     * This method returns all the projects of the system
     * 
     * @return
     */
    @GET
    @AnonymousAllowed
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/projects/all")
    public Response getAllProjects() {
        try {
            checkAuthentication();
            List<ProjectStructure> response = new ArrayList<ProjectStructure>();
            for (Project project : getJiraPluginServiceProvider().getAllProjects()) {
                response.add(new ProjectStructure(String.valueOf(project.getId()), project.getKey(), project.getName(), project.getDescription()));
            }
            return Response.ok(response).build();
        } catch (Exception e) {
            return returnErrorResponseOnException(e);
        }
    }

    /**
     * Create a project according to the provided specifications
     * 
     * @param projectCreationRequest
     * @return
     */
    @POST
    @AnonymousAllowed
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/projects/create")
    public Response createProject(ProjectCreationRequest projectCreationRequest) {
        try {
            checkAuthentication();

            if (projectCreationRequest == null || !projectCreationRequest.isValid()) {
                return returnErrorResponseWithMessageAndErrorCode("Invalid request parameters", ErrorCode.INVALID_PARAMETER);
            }

            if (getJiraPluginServiceProvider().isProjectWithKeyExists(projectCreationRequest.getProjectKey())) {
                ProjectCreationResponse response = new ProjectCreationResponse();
                response.setProjectAlreadyExists(true);
                response.setSuccess(false);
                return Response.ok(response).build();
            }

            String projectRefId = getJiraPluginServiceProvider().createProject(projectCreationRequest.getProjectName(),
                    projectCreationRequest.getProjectKey(), projectCreationRequest.getProjectDescription());
            ProjectCreationResponse response = new ProjectCreationResponse();
            response.setProjectAlreadyExists(false);
            response.setSuccess(true);
            response.setProjectRefId(projectRefId);
            return Response.ok(response).build();
        } catch (Exception e) {
            return returnErrorResponseOnException(e);
        }
    }

    /**
     * This method returns some information about the JIRA instance
     * configuration to be used by BizDock
     * 
     * @return
     */
    @GET
    @AnonymousAllowed
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/config")
    public Response getConfig() {
        try {
            checkAuthentication();
            return Response.ok(getJiraPluginServiceProvider().getJiraInstanceInfo()).build();
        } catch (Exception e) {
            return returnErrorResponseOnException(e);
        }
    }

    /**
     * This method the project associated with the specified key
     * 
     * @param key
     *            a project key
     * @return
     */
    @GET
    @AnonymousAllowed
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/projects/find")
    public Response getProjectFromId(@QueryParam("projectRefId") String projectRefId) {
        try {
            checkAuthentication();
            if (StringUtils.isBlank(projectRefId) || !StringUtils.isNumeric(projectRefId)) {
                return returnErrorResponseWithMessageAndErrorCode("Project RefId cannot be null or blank ", ErrorCode.INVALID_PARAMETER);
            }
            Project project = getJiraPluginServiceProvider().getProjectFromId(Long.valueOf(projectRefId));
            if (project != null) {
                return Response.ok(new ProjectStructure(String.valueOf(project.getId()), project.getKey(), project.getName(), project.getDescription()))
                        .build();
            }
            return Response.ok(new ErrorResponse("Unknown project " + projectRefId)).build();
        } catch (Exception e) {
            return returnErrorResponseOnException(e);
        }
    }

    /**
     * This method the defects associated with the specified data structure
     * 
     * @param requirementsRequestStructure
     *            a request for requirements
     * 
     * @return
     */
    @POST
    @AnonymousAllowed
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/defects/find")
    public Response getDefectsFromProjectId(RequirementsRequestStructure requirementsRequestStructure) {
        try {
            checkAuthentication();
            if (requirementsRequestStructure == null || !requirementsRequestStructure.isValid()) {
                return returnErrorResponseWithMessageAndErrorCode("Invalid request parameters", ErrorCode.INVALID_PARAMETER);
            }
            return Response.ok(
                    getJiraPluginServiceProvider().getDefectsForProject(requirementsRequestStructure.getProjectRefId(),
                            requirementsRequestStructure.getParameters())).build();
        } catch (Exception e) {
            return returnErrorResponseOnException(e);
        }
    }

    /**
     * This method the defects associated with the specified data structure
     * 
     * @param requirementsRequestStructure
     *            a request for requirements
     * 
     * @return
     */
    @POST
    @AnonymousAllowed
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/needs/find")
    public Response getNeedsFromProjectId(RequirementsRequestStructure requirementsRequestStructure) {
        try {
            checkAuthentication();
            if (requirementsRequestStructure == null || !requirementsRequestStructure.isValid()) {
                return returnErrorResponseWithMessageAndErrorCode("Invalid request parameters", ErrorCode.INVALID_PARAMETER);
            }
            return Response.ok(
                    getJiraPluginServiceProvider().getNeedsForProject(requirementsRequestStructure.getProjectRefId(),
                            requirementsRequestStructure.getParameters())).build();
        } catch (Exception e) {
            return returnErrorResponseOnException(e);
        }
    }

    /**
     * Check the API authenticaton
     * 
     * @param path
     *            the request URI (only the path part of the URL)
     * 
     * @throws JiraPluginException
     */
    private void checkAuthentication() throws JiraPluginException {
        String requestURI = getHttpRequest().getRequestURI();
        String queryString = getHttpRequest().getQueryString();
        if (!StringUtils.isBlank(queryString)) {
            requestURI = requestURI + "?" + queryString;
        }
        if (log.isDebugEnabled()) {
            log.debug("Call to " + requestURI + " by " + getApiClientIdentification());
        }
        String authenticationString = getHttpRequest().getHeader(AUTHENTICATION_STRING_HEADER);
        long timeStamp = Long.parseLong(getHttpRequest().getHeader(TIME_STAMP_HEADER));
        if (!getJiraPluginServiceProvider().authenticateClient(authenticationString, timeStamp, requestURI)) {
            throw new JiraPluginException("Authentication failed, API call rejected");
        }
    }

    /**
     * Return an error response matching the specified Exception
     * 
     * @param e
     *            an exception
     * @return
     */
    private Response returnErrorResponseOnException(Exception e) {
        log.error("API call error", e);
        return Response.status(400).entity(new ErrorResponse("API call error", e)).build();
    }

    /**
     * Return an error response with the specified message
     * 
     * @param message
     *            an error message
     * @return
     */
    private Response returnErrorResponseWithMessageAndErrorCode(String message, ErrorCode errorCode) {
        log.error("API call error with message : " + message);
        return Response.status(400).entity(new ErrorResponse("API call error", errorCode)).build();
    }

    /**
     * Return the identification of the API caller (host and ip)
     * 
     * @return
     */
    private String getApiClientIdentification() {
        return "ip [" + getHttpRequest().getRemoteAddr() + "] host [" + getHttpRequest().getRemoteHost() + "]";
    }

    private JiraPluginServiceProvider getJiraPluginServiceProvider() {
        return jiraPluginServiceProvider;
    }

    private HttpServletRequest getHttpRequest() {
        return httpRequest;
    }

    /**
     * A request structure for retrieving reference data
     * 
     * @author Pierre-Yves Cloux
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class PingReponse {
        private String message;
        private String requestURI;
        private String authenticationHeader;
        private String timeStampHeader;
        private boolean authenticated;

        @XmlElement(name = "message")
        public String getMessage() {
            return message;
        }

        public void setMessage(String fieldName) {
            this.message = fieldName;
        }

        @XmlElement(name = "requestURI")
        public String getRequestURI() {
            return requestURI;
        }

        public void setRequestURI(String requestString) {
            this.requestURI = requestString;
        }

        @XmlElement(name = "authenticationHeader")
        public String getAuthenticationHeader() {
            return authenticationHeader;
        }

        public void setAuthenticationHeader(String authenticationHeader) {
            this.authenticationHeader = authenticationHeader;
        }

        @XmlElement(name = "timeStampHeader")
        public String getTimeStampHeader() {
            return timeStampHeader;
        }

        public void setTimeStampHeader(String timeStamp) {
            this.timeStampHeader = timeStamp;
        }

        @XmlElement(name = "authenticated")
        public boolean isAuthenticated() {
            return authenticated;
        }

        public void setAuthenticated(boolean authenticated) {
            this.authenticated = authenticated;
        }
    }

    /**
     * A request structure for providing some details about API issues.<br/>
     * <ul>
     * <li>message : an error message</li>
     * <li>stackTrace : the stack trace of the error</li>
     * <li>configurationIssue : true if the error is related to a configuration
     * issue (requiring some admin actions)</li>
     * </ul>
     * 
     * @author Pierre-Yves Cloux
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ErrorResponse {
        private String message;
        private String stackTrace;
        private ErrorCode errorCode = ErrorCode.UNEXPECTED;

        public enum ErrorCode {
            UNEXPECTED, CONFIGURATION, INVALID_PARAMETER
        }

        public ErrorResponse() {
            super();
        }

        public ErrorResponse(String message) {
            this(message, (Exception) null);
        }

        public ErrorResponse(String message, ErrorCode errorCode) {
            this(message, errorCode, null);
        }

        public ErrorResponse(String message, Exception e) {
            this(message, ErrorCode.UNEXPECTED, e);
        }

        public ErrorResponse(String message, ErrorCode errorCode, Exception e) {
            this.message = message;
            this.errorCode = errorCode;
            if (e != null) {
                StringWriter w = new StringWriter();
                PrintWriter p = new PrintWriter(w);
                e.printStackTrace(p);
                this.stackTrace = w.toString();
                if (e instanceof JiraPluginConfigurationException) {
                    this.errorCode = ErrorCode.CONFIGURATION;
                }
            }
        }

        @XmlElement(name = "message")
        public String getMessage() {
            return message;
        }

        public void setMessage(String fieldName) {
            this.message = fieldName;
        }

        @XmlElement(name = "trace")
        public String getStackTrace() {
            return stackTrace;
        }

        public void setStackTrace(String stackTrace) {
            this.stackTrace = stackTrace;
        }

        @XmlElement(name = "code")
        public ErrorCode getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(ErrorCode errorCode) {
            this.errorCode = errorCode;
        }

    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ProjectStructure {
        private String projectRefId;
        private String projectKey;
        private String projectName;
        private String projectDescription;

        public ProjectStructure() {
            super();
        }

        public ProjectStructure(String projectRefId, String projectKey, String projectName, String projectDescription) {
            super();
            this.projectRefId = projectRefId;
            this.projectKey = projectKey;
            this.projectName = projectName;
            this.projectDescription = projectDescription;
        }

        @XmlElement(name = "name")
        public String getProjectName() {
            return projectName;
        }

        @XmlElement(name = "projectRefId")
        public String getProjectRefId() {
            return projectRefId;
        }

        @XmlElement(name = "description")
        public String getProjectDescription() {
            return projectDescription;
        }

        @XmlElement(name = "key")
        public String getProjectKey() {
            return projectKey;
        }

        public void setProjectKey(String projectKey) {
            this.projectKey = projectKey;
        }
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ProjectCreationRequest {
        private String projectKey;
        private String projectName;
        private String projectDescription;

        public ProjectCreationRequest() {
            super();
        }

        @XmlElement(name = "name")
        public String getProjectName() {
            return projectName;
        }

        @XmlElement(name = "key")
        public String getProjectKey() {
            return projectKey;
        }

        @XmlElement(name = "description")
        public String getProjectDescription() {
            return projectDescription;
        }

        public boolean isValid() {
            return !StringUtils.isBlank(projectName) && !StringUtils.isBlank(projectKey) && StringUtils.isAlpha(projectKey)
                    && !StringUtils.isBlank(projectDescription);
        }
    }

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ProjectCreationResponse {
        private String projectRefId;
        private boolean success = false;
        private boolean projectAlreadyExists = false;

        public ProjectCreationResponse() {
            super();
        }

        @XmlElement(name = "success")
        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        @XmlElement(name = "alreadyExists")
        public boolean isProjectAlreadyExists() {
            return projectAlreadyExists;
        }

        public void setProjectAlreadyExists(boolean projectAlreadyExists) {
            this.projectAlreadyExists = projectAlreadyExists;
        }

        @XmlElement(name = "projectRefId")
        public String getProjectRefId() {
            return projectRefId;
        }

        public void setProjectRefId(String projectRefId) {
            this.projectRefId = projectRefId;
        }

    }

    /**
     * A structure to be used to request some requirements from a project.
     * <ul>
     * <li>projectRefId : the key of the project in JIRA</li>
     * <li>parameters : some parameters to be used in the JQL query template</li>
     * </ul>
     * 
     * @author Pierre-Yves Cloux
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class RequirementsRequestStructure {
        private String projectRefId;
        private Map<String, Object> parameters;

        public RequirementsRequestStructure() {
            super();
        }

        @XmlElement(name = "projectRefId")
        public String getProjectRefId() {
            return projectRefId;
        }

        public void setProjectRefId(String projectRefId) {
            this.projectRefId = projectRefId;
        }

        @XmlElement(name = "parameters")
        public Map<String, Object> getParameters() {
            return parameters;
        }

        public void setParameters(Map<String, Object> parameters) {
            this.parameters = parameters;
        }

        public boolean isValid() {
            return !StringUtils.isBlank(projectRefId) && StringUtils.isNumeric(projectRefId);
        }
    }
}
