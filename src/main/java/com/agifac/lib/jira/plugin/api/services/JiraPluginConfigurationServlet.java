package com.agifac.lib.jira.plugin.api.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agifac.lib.jira.plugin.api.services.JiraPluginServiceProvider.BizDockRequirementsFields;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;

/**
 * The class which providers a configuration GUI for the service
 * 
 * @author Pierre-Yves Cloux
 */
public class JiraPluginConfigurationServlet extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(JiraPluginConfigurationServlet.class);
    private static final long serialVersionUID = 4415284575733417455L;

    private JiraPluginServiceProvider jiraPluginServiceProvider;
    private UserManager userManager;
    private LoginUriProvider loginUriProvider;

    public JiraPluginConfigurationServlet(JiraPluginServiceProvider jiraPluginServiceProvider, UserManager userManager, LoginUriProvider loginUriProvider) {
        this.jiraPluginServiceProvider = jiraPluginServiceProvider;
        this.userManager = userManager;
        this.loginUriProvider = loginUriProvider;
    }

    @Override
    public void init() throws ServletException {
        super.init();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        checkIfUserIsSystemAdmin(request, response);
        String route = request.getPathInfo();
        try {
            if (route.startsWith("/pages")) {
                displayPage(request, response, route);
                return;
            }
            if (route.startsWith("/actions")) {
                // Request to reset the secret key
                if (route.startsWith("/actions/resetSecretKey")) {
                    getJiraPluginServiceProvider().resetSecretKey();
                    redirectToPath(request, response, "/pages/index");
                    return;
                }
                // Request to reset the project configuration
                if (route.startsWith("/actions/resetConfig")) {
                    getJiraPluginServiceProvider().resetConfiguration();
                    redirectToPath(request, response, "/pages/index");
                    return;
                }
                // Request to reset the project creation configuration
                if (route.startsWith("/actions/update_project_creation")) {
                    getJiraPluginServiceProvider().updateUserForProjectCreation(request.getParameter("userForProjectCreation"));
                    redirectToPath(request, response, "/pages/index");
                    return;
                }
                // Request an update of the mapping
                if (route.startsWith("/actions/update_mapping")) {
                    Map<BizDockRequirementsFields, String> mappingBizDockJiraUpdate = new HashMap<BizDockRequirementsFields, String>();
                    Enumeration<String> parameterNames = request.getParameterNames();
                    while (parameterNames.hasMoreElements()) {
                        String name = parameterNames.nextElement();
                        String value = request.getParameter(name);
                        mappingBizDockJiraUpdate.put(BizDockRequirementsFields.valueOf(name), value);
                    }
                    getJiraPluginServiceProvider().updatePluginConfigurationMapping(mappingBizDockJiraUpdate);
                    redirectToPath(request, response, "/pages/index");
                    return;
                }
                // Request an update of the filtering
                if (route.startsWith("/actions/update_filtering")) {
                    String needsJqlQueryTemplate = request.getParameter("needsJqlQueryTemplate");
                    String defectsJqlQueryTemplate = request.getParameter("defectsJqlQueryTemplate");
                    Pair<Boolean, String> status = getJiraPluginServiceProvider().updatePluginConfigurationNeedsJqlQueryTemplate(needsJqlQueryTemplate);
                    if (!status.getLeft()) {
                        displayPage(request, response, "/pages/index", Pair.of("needsJqlQueryTemplateError", status.getRight()));
                        return;
                    }
                    status = getJiraPluginServiceProvider().updatePluginConfigurationDefectsJqlQueryTemplate(defectsJqlQueryTemplate);
                    if (!status.getLeft()) {
                        displayPage(request, response, "/pages/index", Pair.of("defectsJqlQueryTemplateError", status.getRight()));
                        return;
                    }
                    redirectToPath(request, response, "/pages/index");
                    return;
                }
                response.sendError(404);
                return;
            }
            if (route.startsWith("/resources")) {
                returnResource(response, route);
            }
        } catch (Exception e) {
            String message = "Error in BizDock plugin";
            log.error(message, e);
            throw new ServletException(message, e);
        }
    }

    /**
     * The route has been identified as targeting a page.<br/>
     * Display this page using the specified Velocity template (if any).
     * 
     * @param request
     * @param response
     * @param route
     * @param errors
     *            a variable number of errors reported when displaying the page
     *            (after a configuration update)
     * @throws IOException
     */
    private void displayPage(HttpServletRequest request, HttpServletResponse response, String route,
            @SuppressWarnings("unchecked") Pair<String, String>... errors) throws JiraPluginException {
        response.setContentType("text/html");
        try {
            VelocityContext context = new VelocityContext();
            context.put("baseUrl", getJiraPluginServiceProvider().getBaseUrl());
            context.put("rootContext", getPluginServletRootContext(request));
            context.put("secretKey", getJiraPluginServiceProvider().getSecretKey());
            context.put("jiraPluginServiceProvider", getJiraPluginServiceProvider());
            context.put("projectTagInJql", "${" + JiraPluginServiceProvider.PROJECT_TAG_TO_BE_REPLACED + "}");
            context.put("adminUsers", getJiraPluginServiceProvider().getAllAdminUsers());
            for (Pair<String, String> error : errors) {
                context.put(error.getLeft(), error.getRight());
            }
            Template template = getJiraPluginServiceProvider().getVelocityEngine().getTemplate(route + ".vm");
            StringWriter writer = new StringWriter();
            template.merge(context, writer);
            response.getWriter().println(writer.toString());
        } catch (Exception e) {
            throw new JiraPluginException("Error while rendering a page " + route, e);
        }
    }

    /**
     * Redirect to the specified path
     * 
     * @param request
     * @param response
     * @param path
     *            a path relative to the root context
     * @throws IOException
     */
    private void redirectToPath(HttpServletRequest request, HttpServletResponse response, String path) throws JiraPluginException {
        try {
            response.sendRedirect(getPluginServletRootContext(request) + path);
        } catch (IOException e) {
            throw new JiraPluginException("Error while redirecting to the path " + path, e);
        }
    }

    /**
     * Compute the root context of the plugin to enable redirect or pages
     * display
     * 
     * @param request
     * @return
     */
    private String getPluginServletRootContext(HttpServletRequest request) {
        return StringUtils.removeEnd(request.getRequestURI(), request.getPathInfo());
    }

    /**
     * Check if the user calling the servlet is system administrator
     * 
     * @param request
     * @param response
     * @throws IOException
     */
    private void checkIfUserIsSystemAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserProfile userProfile = getUserManager().getRemoteUser();
        if (userProfile == null || !getUserManager().isSystemAdmin(userProfile.getUserKey())) {
            redirectToLogin(request, response);
            return;
        }
    }

    /**
     * Get the data in the webjars
     * 
     * @param response
     *            a servlet response
     * @param route
     *            the route (URL requested by the end user)
     * @throws JiraPluginException
     * @throws IOException
     */
    private void returnResource(HttpServletResponse response, String route) throws JiraPluginException {
        String resourcePath = StringUtils.replaceOnce(route, "/resources", "/META-INF/resources/webjars");
        InputStream inStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (inStream == null) {
            throw new JiraPluginException("Request for a resource which does not exists " + route);
        }

        File file = new File(route);
        String contentType = getServletContext().getMimeType(file.getName());

        // If content type is unknown, then set the default value.
        // For all content types, see:
        // http://www.w3schools.com/media/media_mimeref.asp
        // To add new content types, add new mime-mapping entry in web.xml.
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        response.setContentType(contentType);
        if (log.isDebugEnabled()) {
            log.debug("Request for a resource [" + resourcePath + "] with content type [" + contentType + "]");
        }

        try {
            IOUtils.copy(inStream, response.getOutputStream());
        } catch (Exception e) {
            throw new JiraPluginException("Error while serving a resource " + resourcePath, e);
        }
    }

    private void redirectToLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.sendRedirect(getLoginUriProvider().getLoginUri(getUri(request)).toASCIIString());
    }

    private URI getUri(HttpServletRequest request) {
        StringBuffer builder = request.getRequestURL();
        if (request.getQueryString() != null) {
            builder.append("?");
            builder.append(request.getQueryString());
        }
        return URI.create(builder.toString());
    }

    private JiraPluginServiceProvider getJiraPluginServiceProvider() {
        return jiraPluginServiceProvider;
    }

    private UserManager getUserManager() {
        return userManager;
    }

    private LoginUriProvider getLoginUriProvider() {
        return loginUriProvider;
    }
}
