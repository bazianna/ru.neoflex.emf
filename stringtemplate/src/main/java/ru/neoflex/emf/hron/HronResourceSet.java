package ru.neoflex.emf.hron;

import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class HronResourceSet extends ResourceSetImpl {
    protected Map<String, EClass> nameToEClassMap = new HashMap<>();
    protected Map<String, EObject> nameToEObjectMap = new HashMap<>();
    protected HronSupport delegate;

    public HronResourceSet(HronSupport delegate) {
        super();
        this.delegate = delegate;
        getResourceFactoryRegistry()
                .getExtensionToFactoryMap()
                .put("*", (Resource.Factory) uri -> new HronResource(uri));
    }

    @Override
    public void setPackageRegistry(EPackage.Registry packageRegistry) {
        super.setPackageRegistry(packageRegistry);
        nameToEClassMap = createNameToEClassMap(this.getPackageRegistry());
    }

    public EClass lookupEClass(String nsPrefix, String name) {
        EClass eClass = nameToEClassMap.get(nsPrefix + "." + name);
        if (eClass == null && delegate != null) {
            eClass = delegate.lookupEClass(nsPrefix, name);
        }
        return eClass;
    }

    public EObject lookupEObject(EClass eClass, String qName) {
        EAttribute idAttribute = eClass.getEIDAttribute();
        if (idAttribute == null) {
            return null;
        }
        String key = EcoreUtil.getURI(eClass) + "#" + qName;
        EObject eObject = nameToEObjectMap.get(key);
        if (eObject == null && delegate != null) {
            eObject = delegate.lookupEObject(eClass, qName);
            if (eObject != null) {
                nameToEObjectMap.put(key, eObject);
            }
        }
        return eObject;
    }

    public void resolveAllReferences() {
        nameToEObjectMap.clear();
        for (Iterator<Notifier> it = getAllContents(); it.hasNext();) {
            Notifier notifier = it.next();
            if (notifier instanceof EObject) {
                EObject eObject = (EObject) notifier;
                if (!eObject.eClass().isAbstract()) {
                    EAttribute a = eObject.eClass().getEIDAttribute();
                    if (a != null) {
                        if (eObject.eIsSet(a)) {
                            Object v = eObject.eGet(a);
                            String oName = EcoreUtil.convertToString(a.getEAttributeType(), v);
                            String key = EcoreUtil.getURI(eObject.eClass()) + "#" + oName;
                            nameToEObjectMap.put(key, eObject);
                        }
                    }
                }
            }
        }
        for (Resource resource: getResources()) {
            if (resource instanceof HronResource) {
                HronResource hronResource = (HronResource) resource;
                hronResource.resolve();
            }
        }
    }

    public void parseJar(Path zipFile) {
        Map<String, Object> env = new HashMap<>();
        env.put("useTempFile", Boolean.TRUE);
        java.net.URI uri = java.net.URI.create("jar:" + zipFile.toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, env);) {
            for (Path root: fileSystem.getRootDirectories()) {
                parseDir(root);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int parseZip(InputStream inputStream) throws Exception {
        int entityCount = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream);) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    String entryName = zipEntry.getName();
                    if (entryName.toLowerCase().endsWith(".hron")) {
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int length;
                        while ((length = zipInputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                        ByteArrayInputStream is = new ByteArrayInputStream(outputStream.toByteArray());
                        Resource resource = createResource(URI.createURI(entryName));
                        resource.load(is, null);
                    }
                }
                zipEntry = zipInputStream.getNextEntry();
            }
        }
        resolveAllReferences();
        return entityCount;
    }

    public void parseDir(Path dir) {
        try {
            Stream<Path> paths = Files.walk(dir).filter(path -> Files.isRegularFile(path) && path.endsWith(".hron"));
            parsePaths(paths);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void parsePaths(Stream<Path> paths) {
        paths.forEach(path -> {
            try {
                URI uri = URI.createURI(path.toUri().toString());
                HronResource resource = (HronResource) createResource(uri);
                resource.load(null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        resolveAllReferences();
    }

    public static Map<String, EClass> createNameToEClassMap(EPackage.Registry registry) {
        Map<String, EClass> nameToEClassMap = new HashMap<>();
        for(Object o: registry.values()) {
            EPackage ePackage = (EPackage) o;
            String p = ePackage.getNsPrefix();
            for (EClassifier c: ePackage.getEClassifiers()) {
                if (c instanceof EClass) {
                    EClass eClass = (EClass) c;
                    if (!eClass.isAbstract()) {
                        String n = eClass.getName();
                        nameToEClassMap.put(p + "." + n, eClass);
                    }
                }
            }
        }
        return nameToEClassMap;
    }

}
