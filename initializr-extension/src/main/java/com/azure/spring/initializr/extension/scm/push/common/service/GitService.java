package com.azure.spring.initializr.extension.scm.push.common.service;

import com.azure.spring.initializr.extension.scm.push.common.exception.OAuthAppException;
import com.azure.spring.initializr.extension.scm.push.common.model.User;
import com.azure.spring.initializr.extension.scm.push.common.client.GitRestClient;
import com.azure.spring.initializr.metadata.scm.push.GitPush;
import com.azure.spring.initializr.web.project.ExtendProjectRequest;
import io.spring.initializr.web.project.ProjectGenerationResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.File;
import java.net.URISyntaxException;

public class GitService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitService.class);

    private final GitRestClient gitRestClient;

    private final GitPush.Config gitPushConfig;

    private final String accessToken;

    public GitService(String accessToken, GitRestClient gitRestClient, GitPush.Config gitPushConfig) {
        this.accessToken = accessToken;
        this.gitRestClient = gitRestClient;
        this.gitPushConfig = gitPushConfig;
    }

    /**
     * Push the generated project to remote git repository.
     *
     * @param request the user request
     * @param result the generated project
     * @return
     */
    public String pushToGitRepository(ExtendProjectRequest request, ProjectGenerationResult result) {
        checkParameters(request);

        User user = getUser();

        checkRepositoryExists(user, request);

        String gitRepositoryUrl = createRepository(user, request);
        File path =
                new File(
                        result.getRootDirectory().toFile().getAbsolutePath()
                                + "/"
                                + request.getBaseDir());
        commitAndPush(user.getUsername(), path, gitRepositoryUrl);

        return gitRepositoryUrl;
    }

    private User getUser() {
        return gitRestClient.getUser();
    }

    private String createRepository(User user, ExtendProjectRequest request) {
        return gitRestClient.createRepository(user, request);
    }

    private void checkRepositoryExists(User user, ExtendProjectRequest request) {
        boolean repositoryExists = gitRestClient.repositoryExists(user, request);
        if (repositoryExists) {
            throw new OAuthAppException(
                    "There is already a project named ' "
                            + request.getArtifactId()
                            + "' on your "
                            + request.getGitServiceType()
                            + ", please retry with a different name (the artifact is the name)...");
        }
    }

    private void commitAndPush(String userName, File directory, String gitRepoUrl) {
        try {
            Assert.notNull(userName, "Invalid userName.");

            Git repo = gitCommit(userName, directory);
            gitPush(userName, gitRepoUrl, repo);
            gitClean(repo);
        } catch (GitAPIException gitAPIException) {
            LOGGER.error("An error occurred while pushing to the git repo.", gitAPIException);
            throw new OAuthAppException("An error occurred while pushing to the git repo.");
        } catch (URISyntaxException uriSyntaxException) {
            LOGGER.error("An error occurred while setting gitRepoUrl of the git repo.", uriSyntaxException);
            throw new OAuthAppException("An error occurred while setting gitRepoUrl of the git repo.");
        }
    }

    private void gitClean(Git repo) throws GitAPIException {
        repo.clean().call();
        repo.close();
    }

    private Git gitCommit(String userName, File directory) throws GitAPIException {
        Git repo = Git.init()
                .setInitialBranch(gitPushConfig.getInitDefaultBranch())
                .setDirectory(directory)
                .call();
        repo.add().addFilepattern(".").call();
        String userEmail = gitPushConfig.getInitCommit().getUserEmail();
        repo.commit()
            .setMessage(gitPushConfig.getInitCommit().getMessage())
            .setAuthor(userName, userEmail)
            .setCommitter(userName, userEmail)
            .setSign(false)
            .call();
        return repo;
    }

    private void gitPush(String userName, String gitRepoUrl, Git repo)
            throws GitAPIException, URISyntaxException {
        RemoteAddCommand remote = repo.remoteAdd();
        remote.setName("origin").setUri(new URIish(gitRepoUrl)).call();
        PushCommand pushCommand = repo.push();
        pushCommand.add("HEAD");
        pushCommand.setRemote("origin");
        pushCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, this.accessToken));
        pushCommand.call();
    }

    private void checkParameters(ExtendProjectRequest request) {
        Assert.notNull(request.getArtifactId(), "Invalid request param artifactId.");
        Assert.notNull(request.getBaseDir(), "Invalid request param baseDir.");
        Assert.notNull(request.getCode(), "Invalid request param code.");
        Assert.notNull(request.getName(), "Invalid request param name.");
        Assert.notNull(request.getType(), "Invalid request param type.");
        Assert.notNull(request.getLanguage(), "Invalid request param language.");
        Assert.notNull(request.getArchitecture(), "Request: param architecture.");
        Assert.notNull(request.getPackaging(), "Invalid request param packaging.");
        Assert.notNull(request.getGroupId(), "Invalid request param groupId.");
        Assert.notNull(request.getArtifactId(), "Invalid request param artifactId.");
        Assert.notNull(request.getDescription(), "Invalid request param description.");
        Assert.notNull(request.getPackageName(), "Invalid request param packageName.");
        Assert.notNull(request.getBootVersion(), "Invalid request param bootVersion.");
        Assert.notNull(request.getJavaVersion(), "Invalid request param javaVersion.");
    }
}
