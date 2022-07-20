package org.opencds.cqf.cql.ls.server;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.opencds.cqf.cql.ls.server.manager.CqlTranslationManager;
import org.opencds.cqf.cql.ls.server.plugin.CommandContribution;
import org.opencds.cqf.cql.ls.server.plugin.CqlLanguageServerPlugin;
import org.opencds.cqf.cql.ls.server.plugin.CqlLanguageServerPluginFactory;
import org.opencds.cqf.cql.ls.server.service.CqlTextDocumentService;
import org.opencds.cqf.cql.ls.server.service.CqlWorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CqlLanguageServer implements LanguageServer, LanguageClientAware {
    private static final Logger log = LoggerFactory.getLogger(CqlLanguageServer.class);

    private final CqlWorkspaceService workspaceService;
    private final CqlTextDocumentService textDocumentService;

    private final CqlTranslationManager translationManager;

    private final List<CqlLanguageServerPlugin> plugins;
    private final CompletableFuture<List<CommandContribution>> commandContributions = new CompletableFuture<>();

    private final CompletableFuture<Void> exited;

    private final ServerContext serverContext;

    public CqlLanguageServer() {
        this(new ServerContext(new CompletableFuture<>(), new ActiveContent(), new ArrayList<>()));
    }

    public CqlLanguageServer(ServerContext serverContext) {
        this.serverContext = serverContext;
        this.exited = new CompletableFuture<>();
        this.translationManager = new CqlTranslationManager(this.serverContext.activeContent(), this.serverContext.contentService());
        this.textDocumentService = new CqlTextDocumentService(this.serverContext.client(), this.serverContext.activeContent(), this.translationManager);
        this.workspaceService = new CqlWorkspaceService(this.serverContext.client(), this.commandContributions, this.serverContext.workspaceFolders());
        this.plugins = new ArrayList<>();
        this.loadPlugins();
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        try {

            ServerCapabilities serverCapabilities = new ServerCapabilities();
            this.initializeWorkspaceService(params, serverCapabilities);
            this.initializeTextDocumentService(params, serverCapabilities);

            InitializeResult result = new InitializeResult();
            result.setCapabilities(serverCapabilities);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("failed to initialize with error: {}", e.getMessage());
            return FuturesHelper.failedFuture(e);
        }
    }

    @Override
    public void initialized(InitializedParams params) {
        this.textDocumentService.initialized();
        this.workspaceService.initialized();
    }

    private void initializeTextDocumentService(InitializeParams params, ServerCapabilities serverCapabilities) {
        this.textDocumentService.initialize(params, serverCapabilities);
    }

    private void initializeWorkspaceService(InitializeParams params, ServerCapabilities serverCapabilities) {
        this.workspaceService.initialize(params, serverCapabilities);
    }

    protected void loadPlugins() {
        ServiceLoader<CqlLanguageServerPluginFactory> pluginFactories = ServiceLoader
                .load(CqlLanguageServerPluginFactory.class);

        List<CommandContribution> pluginCommandContributions = new ArrayList<>();

        for (CqlLanguageServerPluginFactory pluginFactory : pluginFactories) {
            CqlLanguageServerPlugin plugin = pluginFactory.createPlugin(this.serverContext.client(), this.workspaceService, this.textDocumentService, this.translationManager);
            this.plugins.add(plugin);
            log.debug("Loading plugin {}", plugin.getName());
            if (plugin.getCommandContribution() != null) {
                pluginCommandContributions.add(plugin.getCommandContribution());
            }
        }

        pluginCommandContributions.add(this.textDocumentService.getCommandContribution());
        pluginCommandContributions.add(new DebugCqlCommandContribution());

        this.commandContributions.complete(pluginCommandContributions);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        this.workspaceService.stop();
        this.textDocumentService.stop();

        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        this.exited.complete(null);
    }

    public CompletableFuture<Void> exited() {
        return this.exited;
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    public CqlTranslationManager getTranslationManager() {
        return translationManager;
    }

    @Override
    public void connect(LanguageClient client) {
        this.serverContext.client().complete(client);
    }
}
