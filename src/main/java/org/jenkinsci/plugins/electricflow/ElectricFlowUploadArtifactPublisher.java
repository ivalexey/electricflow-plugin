
// ElectricFlowUploadArtifactPublisher.java --
//
// ElectricFlowUploadArtifactPublisher.java is part of ElectricCommander.
//
// Copyright (c) 2005-2017 Electric Cloud, Inc.
// All rights reserved.
//

package org.jenkinsci.plugins.electricflow;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import net.sf.json.JSONObject;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;

import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;

import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

public class ElectricFlowUploadArtifactPublisher
    extends Publisher
{

    //~ Static fields/initializers ---------------------------------------------

    private static final Log log = LogFactory.getLog(
            ElectricFlowUploadArtifactPublisher.class);

    //~ Instance fields --------------------------------------------------------

    private final String configuration;
    private final String repositoryName;
    private String       artifactName;
    private String       artifactVersion;
    private String       filePath;

    //~ Constructors -----------------------------------------------------------

    // Fields in config.jelly must match the parameter names in the
    // "DataBoundConstructor"
    @DataBoundConstructor public ElectricFlowUploadArtifactPublisher(
            String repositoryName,
            String artifactName,
            String artifactVersion,
            String filePath,
            String configuration)
    {
        this.repositoryName  = repositoryName;
        this.artifactName    = artifactName;
        this.artifactVersion = artifactVersion;
        this.filePath        = filePath;
        this.configuration   = configuration;
    }

    //~ Methods ----------------------------------------------------------------

    @Override public boolean perform(
            AbstractBuild build,
            Launcher      launcher,
            BuildListener listener)
    {

        try {

            if (log.isDebugEnabled()) {
                log.debug("Publishing artifact...");
            }

            String   workspaceDir;
            FilePath workspace = build.getWorkspace();

            if (workspace != null) {
                workspaceDir = workspace.getRemote();
            }
            else {
                log.warn("Workspace should not be null");

                return false;
            }

            if (log.isDebugEnabled()) {
                log.debug("Workspace directory: " + workspaceDir);
            }

            // let's do a expand variables
            EnvReplacer env                = new EnvReplacer(build, listener);
            String      newFilePath        = env.expandEnv(filePath);
            String      newArtifactVersion = env.expandEnv(artifactVersion);
            String      newArtifactName    = env.expandEnv(artifactName);

            if (log.isDebugEnabled()) {
                log.debug("Workspace directory: " + newFilePath);
            }

            // end of replacements
            ElectricFlowConfigurationManager efCM            =
                new ElectricFlowConfigurationManager();
            Configuration                    cred            =
                efCM.getConfigurationByName(this.configuration);
            String                           electricFlowUrl =
                cred.getElectricFlowUrl();
            String                           userName        =
                cred.getElectricFlowUser();
            String                           userPassword    =
                cred.getElectricFlowPassword();
            ElectricFlowClient               efClient        =
                new ElectricFlowClient(electricFlowUrl, userName, userPassword,
                    workspaceDir);
            String                           result          =
                efClient.uploadArtifact(build, listener, repositoryName,
                    newArtifactName, newArtifactVersion, newFilePath, false);

            if (!"Artifact-Published-OK".equals(result)) {
                listener.getLogger()
                        .println("Upload result: " + result);

                return false;
            }

            String            summaryHtml = getSummaryHtml(newArtifactVersion,
                    newArtifactName, efClient);
            SummaryTextAction action      = new SummaryTextAction(build,
                    summaryHtml);

            build.addAction(action);
            build.save();
            listener.getLogger()
                    .println("Upload result: " + result);
        }
        catch (NoSuchAlgorithmException | KeyManagementException
                | InterruptedException | IOException e) {
            listener.getLogger()
                    .println(e.getMessage());
            log.error(e.getMessage(), e);

            return false;
        }

        return true;
    }

    public String getArtifactName()
    {
        return artifactName;
    }

    public String getArtifactVersion()
    {
        return artifactVersion;
    }

    /**
     * We'll use this from the {@code config.jelly}.
     *
     * @return  we'll use this from the {@code config.jelly}.
     */
    public String getConfiguration()
    {
        return configuration;
    }

    // Overridden for better type safety.
    // If your plugin doesn't really define any property on Descriptor,
    // you don't have to do this.
    @Override public DescriptorImpl getDescriptor()
    {
        return (DescriptorImpl) super.getDescriptor();
    }

    public String getFilePath()
    {
        return filePath;
    }

    public String getRepositoryName()
    {
        return repositoryName;
    }

    @Override public BuildStepMonitor getRequiredMonitorService()
    {
        return BuildStepMonitor.NONE;
    }

    private String getSummaryHtml(
            String             newArtifactVersion,
            String             newArtifactName,
            ElectricFlowClient efClient)
        throws UnsupportedEncodingException
    {
        String url        = efClient.getElectricFlowUrl()
                + "/commander/link/artifactVersionDetails/artifactVersions/"
                + Utils.encodeURL(newArtifactName + ":" + newArtifactVersion)
                + "?s=Artifacts&ss=Artifacts";
        String repository = repositoryName.isEmpty()
            ? "default"
            : repositoryName;

        return "<h3>ElectricFlow Publish Artifact</h3>"
            + "<table cellspacing=\"2\" cellpadding=\"4\">\n"
            + "  <tr>\n"
            + "    <td>Artifact URL:</td>\n"
            + "    <td><a href ='" + url + "'>" + url + "</a></td> \n"
            + "  </tr>\n"
            + "  <tr>\n"
            + "    <td>Artifact Name:</td>\n"
            + "    <td><a href ='" + url + "'>" + artifactName + "</a></td> \n"
            + "  </tr>\n"
            + "  <tr>\n"
            + "    <td>Artifact Version:</td>\n"
            + "    <td>" + newArtifactVersion + "</td> \n"
            + "  </tr>\n"
            + "  <tr>\n"
            + "    <td>Repository Name:</td>\n"
            + "    <td>" + repository + "</td> \n"
            + "  </tr>\n"
            + "</table>";
    }

    //~ Inner Classes ----------------------------------------------------------

    /**
     * The class is marked as public so that it can be accessed from views.
     */
    @Extension // This indicates to Jenkins that this is an implementation of
               // an extension point.
    public static final class DescriptorImpl
        extends BuildStepDescriptor<Publisher>
    {

        //~ Static fields/initializers -----------------------------------------

        private static final Log log = LogFactory.getLog(DescriptorImpl.class);

        //~ Instance fields ----------------------------------------------------

        /**
         * To persist global configuration information, simply store it in a
         * field and call save().
         *
         * <p>If you don't want fields to be persisted, use {@code transient}.
         * </p>
         */
        private String electricFlowUrl;
        private String electricFlowUser;
        private String electricFlowPassword;

        //~ Constructors -------------------------------------------------------

        /**
         * In order to load the persisted global configuration, you have to call
         * load() in the constructor.
         */
        public DescriptorImpl()
        {
            load();
        }

        //~ Methods ------------------------------------------------------------

        @Override public boolean configure(
                StaplerRequest req,
                JSONObject     formData)
            throws FormException
        {
            electricFlowUrl      = formData.getString("electricFlowUrl");
            electricFlowUser     = formData.getString("electricFlowUser");
            electricFlowPassword = formData.getString("electricFlowPassword");

            save();

            return super.configure(req, formData);
        }

        public FormValidation doCheckArtifactName(@QueryParameter String value)
        {
            return Utils.validateValueOnEmpty(value, "Artifact name");
        }

        public FormValidation doCheckArtifactVersion(
                @QueryParameter String value)
        {
            return Utils.validateValueOnEmpty(value, "Artifact version");
        }

        public FormValidation doCheckConfiguration(
                @QueryParameter String value)
        {
            return Utils.validateValueOnEmpty(value, "Configuration");
        }

        public FormValidation doCheckFilePath(@QueryParameter String value)
        {
            return Utils.validateValueOnEmpty(value, "File path");
        }

        public FormValidation doCheckRepositoryName(
                @QueryParameter String value)
        {
            return Utils.validateValueOnEmpty(value, "Repository name");
        }

        public ListBoxModel doFillConfigurationItems()
        {
            return Utils.fillConfigurationItems();
        }

        public ListBoxModel doFillRepositoryNameItems(
                @QueryParameter String configuration)
        {
            ListBoxModel m = new ListBoxModel();

            m.add("Select repository", "");

            if (configuration.isEmpty()) {
                return m;
            }

            try {
                ElectricFlowConfigurationManager efCM         =
                    new ElectricFlowConfigurationManager();
                Configuration                    cred         =
                    efCM.getConfigurationByName(configuration);
                ElectricFlowClient               efClient     =
                    new ElectricFlowClient(cred.getElectricFlowUrl(),
                        cred.getElectricFlowUser(),
                        cred.getElectricFlowPassword());
                List<String>                     repositories;

                repositories = efClient.getArtifactRepositories();

                for (String repo : repositories) {
                    m.add(repo, repo);
                }
            }
            catch (Exception e) {
                log.warn("Error retrieving repository list: "
                        + e.getMessage(), e);

                return m;
            }

            return m;
        }

        public Configuration getConfigurationByName(String name)
        {
            return Utils.getConfigurationByName(name);
        }

        /**
         * This human readable name is used in the configuration screen.
         *
         * @return  this human readable name is used in the configuration
         *          screen.
         */
        @Override public String getDisplayName()
        {
            return "ElectricFlow - Publish Artifact";
        }

        public String getElectricFlowPassword()
        {
            return electricFlowPassword;
        }

        public String getElectricFlowUrl()
        {
            return electricFlowUrl;
        }

        public String getElectricFlowUser()
        {
            return electricFlowUser;
        }

        @Override public boolean isApplicable(
                Class<? extends AbstractProject> aClass)
        {

            // Indicates that this builder can be used with all kinds of
            // project types
            return true;
        }
    }
}
