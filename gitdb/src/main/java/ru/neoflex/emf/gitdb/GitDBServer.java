package ru.neoflex.emf.gitdb;

import com.beijunyi.parallelgit.filesystem.Gfs;
import com.beijunyi.parallelgit.filesystem.GitFileSystem;
import com.beijunyi.parallelgit.utils.RepositoryUtils;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.jgit.lib.Repository;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import static org.eclipse.jgit.lib.Constants.DOT_GIT;

public class GitDBServer extends DBServer {
    private final GitFileSystem gfs;

    public GitDBServer(String dbName, Properties config, List<EPackage> packages) {
        super(dbName, config, packages);
        try {
            String repoPath = config.getProperty("emfdb.git.repo", System.getProperty("user.home") + "/.gitdb/" + this.dbName);
            File repoFile = new File(repoPath);
            repoFile.mkdir();
            Repository repository = new File(repoFile, DOT_GIT).exists() ?
                    RepositoryUtils.openRepository(repoFile, false) :
                    RepositoryUtils.createRepository(repoFile, false);
            gfs = Gfs.newFileSystem(repository);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public GitDBServer(String dbName, Repository repository, List<EPackage> packages) {
        super(dbName, null, packages);
        try {
            gfs = Gfs.newFileSystem(repository);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected DBTransaction createDBTransaction(boolean readOnly, DBServer dbServer, String tenantId) {
        return new GitDBTransaction(readOnly, dbServer);
    }

    @Override
    public String getScheme() {
        return "gitdb";
    }
}
