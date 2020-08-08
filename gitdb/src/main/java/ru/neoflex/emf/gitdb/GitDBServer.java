package ru.neoflex.emf.gitdb;

import com.beijunyi.parallelgit.utils.BranchUtils;
import com.beijunyi.parallelgit.utils.RepositoryUtils;
import com.beijunyi.parallelgit.utils.exceptions.RefUpdateLockFailureException;
import com.beijunyi.parallelgit.utils.exceptions.RefUpdateRejectedException;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.XMLParserPool;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.emf.ecore.xmi.impl.XMLParserPoolImpl;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.eclipse.emf.ecore.xmi.XMLResource.*;
import static org.eclipse.jgit.lib.Constants.DOT_GIT;

public class GitDBServer extends DBServer {
    private Repository repository;
    private Lock lock = new ReentrantLock();
    private XMLParserPool xmlParserPool = new XMLParserPoolImpl();

    public GitDBServer(String dbName, Properties config, List<EPackage> packages) {
        super(dbName, config, packages);
        try {
            String repoPath = config.getProperty("emfdb.git.repo", System.getProperty("user.home") + "/.githome/" + this.getDbName());
            File repoFile = new File(repoPath);
            repoFile.mkdir();
            repository = new File(repoFile, DOT_GIT).exists() ?
                    RepositoryUtils.openRepository(repoFile, false) :
                    RepositoryUtils.createRepository(repoFile, false);
            if (BranchUtils.getBranches(repository).size() == 0) {
                try (Git git = new Git(repository);) {
                    git.commit().setMessage("Initial commit").setAllowEmpty(true).call();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public GitDBServer(String dbName, Repository repository, List<EPackage> packages) {
        super(dbName, new Properties(), packages);
        this.repository = repository;
    }

    @Override
    protected DBTransaction createDBTransaction(boolean readOnly, DBServer dbServer, String tenantId) {
        return new GitDBTransaction(readOnly, dbServer);
    }

    @Override
    public String getScheme() {
        return "gitdb";
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    public Resource createResource(URI uri) {
        XMIResourceImpl resource = new XMIResourceImpl(uri);
        resource.getDefaultSaveOptions().put(OPTION_ENCODING, "UTF-8");
        resource.getDefaultSaveOptions().put(OPTION_CONFIGURATION_CACHE, true);
        resource.getDefaultLoadOptions().put(OPTION_USE_PARSER_POOL, xmlParserPool);
        resource.getDefaultLoadOptions().put(OPTION_USE_DEPRECATED_METHODS, false);
        resource.getDefaultLoadOptions().put(OPTION_DEFER_ATTACHMENT, true);
        resource.getDefaultLoadOptions().put(OPTION_DEFER_IDREF_RESOLUTION, true);
        return resource;
    }


    protected DBServer.TxRetryStrategy createTxRetryStrategy() {
        DBServer.TxRetryStrategy retryStrategy = super.createTxRetryStrategy();
        retryStrategy.retryClasses.add(RefUpdateLockFailureException.class);
        retryStrategy.retryClasses.add(RefUpdateRejectedException.class);
        return retryStrategy;
    }

    public Lock getLock() {
        return lock;
    }
}
