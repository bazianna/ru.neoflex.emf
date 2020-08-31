package ru.neoflex.emf.gitdb;

import com.beijunyi.parallelgit.filesystem.Gfs;
import com.beijunyi.parallelgit.filesystem.GitFileSystem;
import com.beijunyi.parallelgit.filesystem.GitPath;
import com.beijunyi.parallelgit.filesystem.commands.GfsCommit;
import com.beijunyi.parallelgit.filesystem.io.DirectoryNode;
import com.beijunyi.parallelgit.filesystem.io.Node;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import ru.neoflex.emf.base.DBObject;
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.stream.Stream;

public class GitDBTransaction extends DBTransaction {
    public static final String INDEXES_DIR_NAME = "indexes";
    public static final String DB_OBJECTS_DIR_NAME = "db-objects";
    public static final String REFERENCES_DIR_NAME = "references";
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    static SecureRandom prng;

    static {
        try {
            prng = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final GitFileSystem gfs;

    public GitDBTransaction(boolean readOnly, DBServer dbServer) {
        super(readOnly, dbServer);
        if (!isReadOnly() && getGitDBServer().getLock() != null) {
            getGitDBServer().getLock().lock();
        }
        try {
            gfs = Gfs.newFileSystem(getGitDBServer().getTenantId(), getGitDBServer().getRepository());
        } catch (IOException e) {
            if (!isReadOnly() && getGitDBServer().getLock() != null) {
                getGitDBServer().getLock().unlock();
            }
            throw new RuntimeException(e);
        }
    }

    static ObjectId getObjectId(GitPath path) {
        try {
            if (!path.isAbsolute()) throw new IllegalArgumentException(path.toString());
            Node current = path.getFileStore().getRoot();
            for (int i = 0; i < path.getNameCount(); i++) {
                GitPath name = path.getName(i);
                if (current instanceof DirectoryNode) {
                    current = ((DirectoryNode) current).getChild(name.toString());
                    if (current == null) {
                        return null;
                    }
                } else
                    return null;
            }
            return current.getObjectId(false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static String getVersion(GitPath path) {
        ObjectId objectId = getObjectId(path);
        return objectId == null ? null : objectId.getName().substring(0, 16);
    }

    public static String getRandomId(int length) {
        byte[] bytes = new byte[length];
        prng.nextBytes(bytes);
        return hex(bytes);
    }

    private static String hex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public GitDBServer getGitDBServer() {
        return (GitDBServer) getDbServer();
    }

    @Override
    protected DBResource get(String id) {
        try {
            GitPath imagePath = getResourcePath(id);
            String rev = getVersion(imagePath);
            if (rev == null) {
                return null;
            }
            byte[] content = Files.readAllBytes(imagePath);
            DBResource dbResource = new DBResource();
            dbResource.setId(id);
            dbResource.setVersion(rev);
            dbResource.setImage(content);
            fillIndexes(dbResource);
            return dbResource;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Stream<DBResource> findByPath(String path) {
        try {
            GitPath resourcesPath = gfs.getPath("/db/resources");
            GitPath searchPath = resourcesPath.resolve(path);
            return Files.walk(searchPath)
                    .filter(Files::isRegularFile)
                    .map(imagePath -> {
                        String resid = resourcesPath.relativize(imagePath).toString();
                        return getOrThrow(resid);
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void fillIndexes(DBResource dbResource) {
        ResourceSet rs = createResourceSet();
        Resource resource = rs.createResource(getDbServer().createURI(dbResource.getId()));
        load(dbResource, resource);
        fillIndexes(resource, dbResource);
    }

    @Override
    protected Stream<DBResource> findAll() {
        try {
            GitPath resourcesPath = gfs.getPath("/db/resources");
            if (!Files.isDirectory(resourcesPath)) {
                return Stream.empty();
            }
            return Files.walk(resourcesPath)
                    .filter(Files::isRegularFile)
                    .map(imagePath -> {
                        String id = resourcesPath.relativize(imagePath).toString();
                        return getOrThrow(id);
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String escape(String s) {
        return s.replaceAll("\\W+", "_");
    }

    @Override
    protected Stream<DBResource> findByClass(String classUri) {
        try {
            GitPath indexRoot = gfs.getPath("/db/",
                    INDEXES_DIR_NAME, DB_OBJECTS_DIR_NAME, escape(classUri));
            if (!Files.isDirectory(indexRoot)) {
                return Stream.empty();
            }
            return Files.walk(indexRoot)
                    .filter(Files::isRegularFile)
                    .map(indexPath -> {
                        try {
                            String id = new String(Files.readAllBytes(indexPath));
                            return get(id);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Stream<DBResource> findByClassAndQName(String classUri, String qName) {
        try {
            GitPath indexPath = gfs.getPath("/db/",
                    INDEXES_DIR_NAME, DB_OBJECTS_DIR_NAME, escape(classUri), escape(qName));
            if (Files.isRegularFile(indexPath)) {
                String id = new String(Files.readAllBytes(indexPath));
                return Stream.of(getOrThrow(id));
            } else {
                return Stream.empty();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Stream<DBResource> findReferencedTo(String id) {
        try {
            GitPath indexRoot = gfs.getPath("/db/",
                    INDEXES_DIR_NAME, REFERENCES_DIR_NAME, id);
            if (!Files.isDirectory(indexRoot)) {
                return Stream.empty();
            }
            return Files.walk(indexRoot)
                    .filter(Files::isRegularFile)
                    .map(indexPath -> {
                        String refId = indexRoot.relativize(indexPath).toString();
                        return getOrThrow(refId);
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void insert(DBResource dbResource) {
        try {
            GitPath imagePath = getResourcePath(dbResource.getId());
            Files.createDirectories(imagePath.getParent());
            Files.write(imagePath, dbResource.getImage());
            createNewIndexes(dbResource);
            String rev = getVersion(imagePath);
            dbResource.setVersion(rev);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private GitPath getResourcePath(String id) {
        return gfs.getPath("/db/resources/", id);
    }

    @Override
    protected void update(DBResource oldDbResource, DBResource dbResource) {
        try {
            GitPath imagePath = getResourcePath(dbResource.getId());
            Files.write(imagePath, dbResource.getImage());
            deleteOldIndexes(oldDbResource);
            createNewIndexes(dbResource);
            String rev = getVersion(imagePath);
            dbResource.setVersion(rev);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteOldIndexes(DBResource dbResource) throws IOException {
        for (DBObject dbObject : dbResource.getDbObjects()) {
            GitPath indexPath = gfs.getPath("/db/", INDEXES_DIR_NAME,
                    DB_OBJECTS_DIR_NAME, escape(dbObject.getClassUri()), escape(dbObject.getQName()));
            Files.delete(indexPath);
        }
        for (String reference : dbResource.getReferences()) {
            GitPath indexPath = gfs.getPath("/db/", INDEXES_DIR_NAME,
                    REFERENCES_DIR_NAME, reference, dbResource.getId());
            Files.delete(indexPath);
        }
    }

    private void createNewIndexes(DBResource dbResource) throws IOException {
        for (DBObject dbObject : dbResource.getDbObjects()) {
            GitPath indexPath = gfs.getPath("/db/", INDEXES_DIR_NAME,
                    DB_OBJECTS_DIR_NAME, escape(dbObject.getClassUri()), escape(dbObject.getQName()));
            Files.createDirectories(indexPath.getParent());
            Files.write(indexPath, dbResource.getId().getBytes());
        }
        for (String reference : dbResource.getReferences()) {
            GitPath indexPath = gfs.getPath("/db/", INDEXES_DIR_NAME,
                    REFERENCES_DIR_NAME, reference, dbResource.getId());
            Files.createDirectories(indexPath.getParent());
            Files.write(indexPath, new byte[0]);
        }
    }

    @Override
    protected void delete(DBResource dbResource) {
        try {
            GitPath imagePath = getResourcePath(dbResource.getId());
            DBResource oldDbResource = new DBResource();
            fillIndexes(oldDbResource);
            Files.delete(imagePath);
            deleteOldIndexes(oldDbResource);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean truncate() {
        GitPath dbPath = gfs.getPath("/db");
        try {
            Files.walk(dbPath)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            return true;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    protected String getNextId() {
        while (true) {
            String next = getRandomId(8);
            String id = next.substring(0, 2) + "/" + next.substring(2) + ".xmi";
            GitPath imagePath = getResourcePath(id);
            if (!Files.exists(imagePath)) {
                return id;
            }
        }
    }

    @Override
    public void commit() {
        if (!isReadOnly()) {
            try {
                GfsCommit commit = Gfs.commit(gfs).message(message);
                if (author != null && email != null) {
                    PersonIdent authorId = new PersonIdent(author, email);
                    commit.author(authorId);
                }
                commit.execute();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void close() throws Exception {
        gfs.close();
        if (!isReadOnly() && getGitDBServer().getLock() != null) {
            getGitDBServer().getLock().unlock();
        }
    }
}
