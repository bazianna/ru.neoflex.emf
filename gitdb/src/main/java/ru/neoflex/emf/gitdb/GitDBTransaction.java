package ru.neoflex.emf.gitdb;

import com.beijunyi.parallelgit.filesystem.Gfs;
import com.beijunyi.parallelgit.filesystem.GitFileSystem;
import com.beijunyi.parallelgit.filesystem.GitPath;
import com.beijunyi.parallelgit.filesystem.io.DirectoryNode;
import com.beijunyi.parallelgit.filesystem.io.Node;
import org.eclipse.jgit.lib.ObjectId;
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.stream.Stream;

public class GitDBTransaction extends DBTransaction {
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
        try {
            gfs = Gfs.newFileSystem(getGitDBServer().getRepository());
        } catch (IOException e) {
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
            String idDir = id.substring(0, 2);
            String idFile = id.substring(2);
            GitPath path = gfs.getPath("/", getGitDBServer().getDbName(), "ids", idDir, idFile);
            ObjectId objectId = getObjectId(path);
            if (objectId == null) {
                throw new IllegalArgumentException("Entity not found: " + id);
            }
            String rev = objectId.getName();
            byte[] content = Files.readAllBytes(path);
            DBResource dbResource = new DBResource();
            dbResource.setId(id);
            dbResource.setVersion(rev);
            dbResource.setImage(content);
            return dbResource;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Stream<DBResource> findAll() {
        return null;
    }

    @Override
    protected Stream<DBResource> findByClass(String classUri) {
        return null;
    }

    @Override
    protected Stream<DBResource> findByClassAndQName(String classUri, String qName) {
        return null;
    }

    @Override
    protected Stream<DBResource> findReferencedTo(String id) {
        return null;
    }

    @Override
    protected void insert(DBResource dbResource) {
        try {
            dbResource.setId(getNextId());
            String idDir = dbResource.getId().substring(0, 2);
            String idFile = dbResource.getId().substring(2);
            GitPath path = gfs.getPath("/", getGitDBServer().getDbName(), "ids", idDir, idFile);
            Files.createDirectories(path.getParent());
            Files.write(path, dbResource.getImage());
            ObjectId objectId = getObjectId(path);
            dbResource.setVersion(objectId.getName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void update(String id, DBResource dbResource) {
         try {
             String idDir = id.substring(0, 2);
             String idFile = id.substring(2);
             GitPath path = gfs.getPath("/", getGitDBServer().getDbName(), "ids", idDir, idFile);
             Files.write(path, dbResource.getImage());
             ObjectId objectId = getObjectId(path);
             dbResource.setVersion(objectId.getName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void delete(String id) {
        try {
            String idDir = id.substring(0, 2);
            String idFile = id.substring(2);
            GitPath path = gfs.getPath("/", getGitDBServer().getDbName(), "ids", idDir, idFile);
            Files.delete(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected String getNextId() {
        return getRandomId(16);
    }

}
