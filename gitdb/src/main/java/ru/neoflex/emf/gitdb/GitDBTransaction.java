package ru.neoflex.emf.gitdb;

import com.beijunyi.parallelgit.filesystem.Gfs;
import com.beijunyi.parallelgit.filesystem.GitFileSystem;
import com.beijunyi.parallelgit.filesystem.GitPath;
import com.beijunyi.parallelgit.filesystem.commands.GfsCommit;
import com.beijunyi.parallelgit.filesystem.io.DirectoryNode;
import com.beijunyi.parallelgit.filesystem.io.Node;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import ru.neoflex.emf.base.DBObject;
import ru.neoflex.emf.base.DBResource;
import ru.neoflex.emf.base.DBServer;
import ru.neoflex.emf.base.DBTransaction;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.stream.Stream;

public class GitDBTransaction extends DBTransaction {
    public static final String IMAGE_FILE_NAME = "image.xmi";
    public static final String INDEXES_FILE_NAME = "indexes.xml";
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

    private static byte[] writeIndexesContent(DBResource dbResource) {
        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
            Document document = documentBuilder.newDocument();
            Element rootElement = document.createElement("indexes");
            document.appendChild(rootElement);
            for (DBObject dbObject : dbResource.getDbObjects()) {
                Element element = document.createElement("db-object");
                element.setAttribute("class-uri", dbObject.getClassUri());
                element.setAttribute("q-name", dbObject.getQName());
                rootElement.appendChild(element);
            }
            for (String reference : dbResource.getReferences()) {
                Element element = document.createElement("reference");
                element.setAttribute("id", reference);
                rootElement.appendChild(element);
            }
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(document);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            StreamResult result = new StreamResult(stream);
            transformer.transform(source, result);
            return stream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void readIndexesContent(byte[] indexes, DBResource dbResource) {
        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
            ByteArrayInputStream stream = new ByteArrayInputStream(indexes);
            Document document = documentBuilder.parse(stream);
            dbResource.getDbObjects().clear();
            NodeList dbObjectNodes = document.getElementsByTagName("db-object");
            for (int i = 0; i < dbObjectNodes.getLength(); ++i) {
                Element node = (Element) dbObjectNodes.item(i);
                DBObject dbObject = new DBObject();
                dbObject.setClassUri(node.getAttribute("class-uri"));
                dbObject.setQName(node.getAttribute("q-name"));
                dbResource.getDbObjects().add(dbObject);
            }
            dbResource.getReferences().clear();
            NodeList referenceNodes = document.getElementsByTagName("reference");
            for (int i = 0; i < referenceNodes.getLength(); ++i) {
                Element node = (Element) referenceNodes.item(i);
                dbResource.getReferences().add(node.getAttribute("id"));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public GitDBServer getGitDBServer() {
        return (GitDBServer) getDbServer();
    }

    @Override
    protected DBResource get(String id) {
        GitPath resourcePath = getResourcePath(id);
        return getDbResource(id, resourcePath);
    }

    private DBResource getDbResource(String id, GitPath resourcePath) {
        try {
            GitPath imagePath = resourcePath.resolve(IMAGE_FILE_NAME);
            ObjectId objectId = getObjectId(imagePath);
            if (objectId == null) {
                throw new IllegalArgumentException("Entity not found: " + id);
            }
            String rev = objectId.getName();
            byte[] content = Files.readAllBytes(imagePath);
            DBResource dbResource = new DBResource();
            dbResource.setId(id);
            dbResource.setVersion(rev);
            dbResource.setImage(content);
            GitPath indexesPath = resourcePath.resolve(INDEXES_FILE_NAME);
            byte[] indexes = Files.readAllBytes(indexesPath);
            readIndexesContent(indexes, dbResource);
            return dbResource;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Stream<DBResource> findAll() {
        try {
            GitPath resourcesPath = gfs.getPath("/", getGitDBServer().getDbName(), "resources");
            return Files.walk(resourcesPath)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(IMAGE_FILE_NAME))
                    .map(imagePath -> {
                        GitPath parent = (GitPath) imagePath.getParent();
                        String id1 = parent.getParent().getFileName().toString();
                        String id2 = parent.getFileName().toString();
                        return getDbResource(id1 + id2, parent);
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
            GitPath indexRoot = gfs.getPath("/", getGitDBServer().getDbName(),
                    INDEXES_DIR_NAME, DB_OBJECTS_DIR_NAME, escape(classUri));
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
            GitPath indexPath = gfs.getPath("/", getGitDBServer().getDbName(),
                    INDEXES_DIR_NAME, DB_OBJECTS_DIR_NAME, escape(classUri), escape(qName));
            if (Files.isRegularFile(indexPath)) {
                String id = new String(Files.readAllBytes(indexPath));
                return Stream.of(get(id));
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
            GitPath indexRoot = gfs.getPath("/", getGitDBServer().getDbName(),
                    INDEXES_DIR_NAME, REFERENCES_DIR_NAME, id.substring(0, 2), id.substring(2));
            return Files.walk(indexRoot)
                    .filter(Files::isRegularFile)
                    .map(indexPath -> {
                        String refId = indexPath.getFileName().toString();
                        return get(refId);
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void insert(DBResource dbResource) {
        try {
            dbResource.setId(getNextId());
            GitPath resourcePath = getResourcePath(dbResource.getId());
            GitPath imagePath = resourcePath.resolve(IMAGE_FILE_NAME);
            Files.createDirectories(imagePath.getParent());
            Files.write(imagePath, dbResource.getImage());
            GitPath indexesPath = resourcePath.resolve(INDEXES_FILE_NAME);
            byte[] indexes = writeIndexesContent(dbResource);
            Files.write(indexesPath, indexes);
            createNewIndexes(dbResource);
            ObjectId objectId = getObjectId(imagePath);
            dbResource.setVersion(objectId.getName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private GitPath getResourcePath(String id) {
        String id1 = id.substring(0, 2);
        String id2 = id.substring(2);
        return gfs.getPath("/", getGitDBServer().getDbName(), "resources", id1, id2);
    }

    @Override
    protected void update(DBResource dbResource) {
        try {
            GitPath resourcePath = getResourcePath(dbResource.getId());
            GitPath imagePath = resourcePath.resolve(IMAGE_FILE_NAME);
            GitPath indexesPath = resourcePath.resolve(INDEXES_FILE_NAME);
            byte[] oldIndexes = Files.readAllBytes(indexesPath);
            DBResource oldDbResource = new DBResource();
            oldDbResource.setId(dbResource.getId());
            readIndexesContent(oldIndexes, oldDbResource);
            Files.write(imagePath, dbResource.getImage());
            byte[] indexes = writeIndexesContent(dbResource);
            Files.write(indexesPath, indexes);
            deleteOldIndexes(oldDbResource);
            createNewIndexes(dbResource);
            ObjectId objectId = getObjectId(imagePath);
            dbResource.setVersion(objectId.getName());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void deleteOldIndexes(DBResource dbResource) throws IOException {
        for (DBObject dbObject : dbResource.getDbObjects()) {
            GitPath indexPath = gfs.getPath("/", getGitDBServer().getDbName(), INDEXES_DIR_NAME,
                    DB_OBJECTS_DIR_NAME, escape(dbObject.getClassUri()), escape(dbObject.getQName()));
            Files.delete(indexPath);
        }
        for (String reference : dbResource.getReferences()) {
            GitPath indexPath = gfs.getPath("/", getGitDBServer().getDbName(), INDEXES_DIR_NAME,
                    REFERENCES_DIR_NAME, reference.substring(0, 2), reference.substring(2), dbResource.getId());
            Files.delete(indexPath);
        }
    }

    private void createNewIndexes(DBResource dbResource) throws IOException {
        for (DBObject dbObject : dbResource.getDbObjects()) {
            GitPath indexPath = gfs.getPath("/", getGitDBServer().getDbName(), INDEXES_DIR_NAME,
                    DB_OBJECTS_DIR_NAME, escape(dbObject.getClassUri()), escape(dbObject.getQName()));
            Files.createDirectories(indexPath.getParent());
            Files.write(indexPath, dbResource.getId().getBytes());
        }
        for (String reference : dbResource.getReferences()) {
            GitPath indexPath = gfs.getPath("/", getGitDBServer().getDbName(), INDEXES_DIR_NAME,
                    REFERENCES_DIR_NAME, reference.substring(0, 2), reference.substring(2), dbResource.getId());
            Files.createDirectories(indexPath.getParent());
            Files.write(indexPath, new byte[0]);
        }
    }

    @Override
    protected void delete(String id) {
        try {
            GitPath resourcePath = getResourcePath(id);
            GitPath imagePath = resourcePath.resolve(IMAGE_FILE_NAME);
            GitPath indexesPath = resourcePath.resolve(INDEXES_FILE_NAME);
            byte[] oldIndexes = Files.readAllBytes(indexesPath);
            DBResource oldDbResource = new DBResource();
            oldDbResource.setId(id);
            readIndexesContent(oldIndexes, oldDbResource);
            Files.delete(imagePath);
            Files.delete(indexesPath);
            deleteOldIndexes(oldDbResource);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected String getNextId() {
        return getRandomId(16);
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
