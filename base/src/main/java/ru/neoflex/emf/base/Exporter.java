package ru.neoflex.emf.base;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Exporter {
    public static final String XMI = ".xmi";
    public static final String REFS_XML = ".refs.xml";
    HbServer hbServer;

    public Exporter(HbServer hbServer) {
        this.hbServer = hbServer;
    }

    public byte[] exportEObjectWithoutExternalRefs(EObject eObject) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
        Resource resource = resourceSet.createResource(eObject.eResource().getURI());
        EObject copyObject = EcoreUtil.copy(eObject);
        resource.getContents().add(copyObject);
        unsetExternalReferences(copyObject);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        resource.save(os, null);
        return os.toByteArray();
    }

    public void unsetExternalReferences(EObject eObject) {
        Map<EObject, Collection<EStructuralFeature.Setting>> crs = EcoreUtil.ExternalCrossReferencer.find(Collections.singleton(eObject));
        for (EObject refObject : crs.keySet()) {
            for (EStructuralFeature.Setting setting : crs.get(refObject)) {
                EStructuralFeature sf = setting.getEStructuralFeature();
                if (!sf.isMany()) {
                    setting.unset();
                } else {
                    EObject owner = setting.getEObject();
                    EList eList = (EList) owner.eGet(sf);
                    eList.remove(refObject);
                }
            }
        }
    }

    public Element objectToElement(Document document, EObject eObject) throws ParserConfigurationException {
        Element element = document.createElement("e-object");
        EObject rootContainer = EcoreUtil.getRootContainer(eObject);
        String fragment = EcoreUtil.getRelativeURIFragmentPath(rootContainer, eObject);
        String qName = hbServer.getQName(rootContainer);
        if (qName == null || qName.length() == 0) {
            throw new IllegalArgumentException("No qName");
        }
        element.setAttribute("e-class", EcoreUtil.getURI(rootContainer.eClass()).toString());
        element.setAttribute("q-name", qName);
        element.setAttribute("fragment", fragment);
        return element;
    }

    protected EObject elementToObject(Element element, HbTransaction tx) throws Exception {
        ResourceSet rs = tx.getResourceSet();
        String classUri = element.getAttribute("e-class");
        EClass eClass = (EClass) rs.getEObject(URI.createURI(classUri), false);
        String qName = element.getAttribute("q-name");
        String fragment = element.getAttribute("fragment");
        List<EObject> eObjects = tx.findByClassAndQName(rs, eClass, qName)
                .flatMap(resource -> resource.getContents().stream())
                .map(eObject -> fragment.length() == 0 ? eObject : EcoreUtil.getEObject(eObject, fragment))
                .filter(eObject -> eObject != null)
                .collect(Collectors.toList());
        if (eObjects.size() == 0) {
            throw new IllegalArgumentException(String.format("EObject not found: %s[%s/$s]",
                    classUri, qName, fragment));
        }
        return eObjects.get(0);
    }

    public byte[] exportExternalRefs(EObject eObject) throws ParserConfigurationException, TransformerException {
        Map<EObject, Collection<EStructuralFeature.Setting>> crs = EcoreUtil.ExternalCrossReferencer.find(Collections.singleton(eObject));
        if (crs.size() == 0) {
            return null;
        }
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
        Document document = documentBuilder.newDocument();
        Element rootElement = objectToElement(document, eObject);
        document.appendChild(rootElement);
        for (EObject refObject : crs.keySet()) {
            Element referenceObjectElement = objectToElement(document, refObject);
            rootElement.appendChild(referenceObjectElement);
            for (EStructuralFeature.Setting setting : crs.get(refObject)) {
                Element referenceElement = document.createElement("reference");
                referenceObjectElement.appendChild(referenceElement);
                referenceElement.setAttribute("feature", setting.getEStructuralFeature().getName());
                EObject child = setting.getEObject();
                String fragment = EcoreUtil.getRelativeURIFragmentPath(eObject, child);
                referenceElement.setAttribute("fragment", fragment);
                EStructuralFeature sf = setting.getEStructuralFeature();
                int index = -1;
                if (sf.isMany()) {
                    EList eList = (EList) child.eGet(sf);
                    index = eList.indexOf(refObject);
                }
                referenceElement.setAttribute("index", String.valueOf(index));
            }
        }
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(document);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        StreamResult result = new StreamResult(stream);
        transformer.transform(source, result);
        return stream.toByteArray();
    }

    public String getFileName(EObject eObject) {
        EClass eClass = eObject.eClass();
        EPackage ePackage = eClass.getEPackage();
        String qName = hbServer.getQName(eObject);
        if (qName == null || qName.length() == 0) {
            throw new IllegalArgumentException("No qName");
        }
        String fileName = ePackage.getNsPrefix() + "_" + eClass.getName() + "_" + qName;
        return fileName;
    }

    public void exportEObject(EObject eObject, Path path) {
        try {
            String fileName = getFileName(eObject);
            byte[] bytes = exportEObjectWithoutExternalRefs(eObject);
            Path filePath = path.resolve(fileName + XMI);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, bytes);
            byte[] refsBytes = exportExternalRefs(eObject);
            if (refsBytes != null) {
                Path refsPath = path.resolve(fileName + REFS_XML);
                Files.createDirectories(refsPath.getParent());
                Files.write(refsPath, refsBytes);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void zipEObject(EObject eObject, ZipOutputStream zipOutputStream) {
        try {
            String fileName = getFileName(eObject);
            byte[] bytes = exportEObjectWithoutExternalRefs(eObject);
            ZipEntry zipEntry = new ZipEntry(fileName + XMI);
            zipOutputStream.putNextEntry(zipEntry);
            zipOutputStream.write(bytes);
            zipOutputStream.closeEntry();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void zipEObjectReferences(EObject eObject, ZipOutputStream zipOutputStream) {
        try {
            String fileName = getFileName(eObject);
            byte[] bytes = exportExternalRefs(eObject);
            if (bytes != null) {
                ZipEntry zipEntry = new ZipEntry(fileName + REFS_XML);
                zipOutputStream.putNextEntry(zipEntry);
                zipOutputStream.write(bytes);
                zipOutputStream.closeEntry();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void zip(List<Resource> resources, OutputStream outputStream) {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);) {
            for (Resource resource : resources) {
                for (EObject eObject : resource.getContents()) {
                    zipEObject(eObject, zipOutputStream);
                }
            }
            for (Resource resource : resources) {
                for (EObject eObject : resource.getContents()) {
                    zipEObjectReferences(eObject, zipOutputStream);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void exportZipFile(List<Resource> resources, Path zipFile) throws IOException {
        Files.createDirectories(zipFile.getParent());
        try (OutputStream os = Files.newOutputStream(zipFile)) {
            zip(resources, os);
        }
    }

    public void export(Stream<Resource> resources, Path path) {
        resources.flatMap(resource -> resource.getContents().stream()).forEach(eObject -> {
            exportEObject(eObject, path);
        });
    }

    private List<Resource> importResource(String fileName, byte[] bytes, HbTransaction tx) throws IOException {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.setPackageRegistry(hbServer.getPackageRegistry());
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap()
                .put(Resource.Factory.Registry.DEFAULT_EXTENSION, new XMIResourceFactoryImpl());
        Resource resourceIn = resourceSet.createResource(URI.createURI(fileName));
        resourceIn.load(new ByteArrayInputStream(bytes), null);
        ResourceSet rs = tx.getResourceSet();
        List<Resource> result = new ArrayList<>();
        for (EObject eObject : resourceIn.getContents()) {
            String qName = hbServer.getQName(eObject);
            if (qName == null || qName.length() == 0) {
                throw new IllegalArgumentException("No qName");
            }
            List<Resource> resources = tx.findByClassAndQName(rs, eObject.eClass(), qName).collect(Collectors.toList());
            URI uri = resources.size() == 0 ? hbServer.createURI(null) : resources.get(0).getURI();
            Resource resource = rs.createResource(uri);
            resource.getContents().add(EcoreUtil.copy(eObject));
            resource.save(null);
            result.add(resource);
        }
        return result;
    }

    private EObject importExternalReferences(byte[] bytes, HbTransaction tx) throws Exception {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = builderFactory.newDocumentBuilder();
        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
        Document document = documentBuilder.parse(stream);
        Element rootElement = document.getDocumentElement();
        EObject eObject = elementToObject(rootElement, tx);
        unsetExternalReferences(eObject);
        List<Setting> settings = new ArrayList<>();
        NodeList refObjectNodes = rootElement.getElementsByTagName("e-object");
        for (int i = 0; i < refObjectNodes.getLength(); ++i) {
            Element refObjectNode = (Element) refObjectNodes.item(i);
            EObject refObject = elementToObject(refObjectNode, tx);
            NodeList refNodes = rootElement.getElementsByTagName("reference");
            for (int j = 0; j < refNodes.getLength(); ++j) {
                Element refNode = (Element) refNodes.item(j);
                Setting setting = new Setting();
                setting.refObject = refObject;
                String fragment = refNode.getAttribute("fragment");
                setting.referenceeObject = EcoreUtil.getEObject(eObject, fragment);
                String feature = refNode.getAttribute("feature");
                setting.eReference = (EReference) setting.referenceeObject.eClass().getEStructuralFeature(feature);
                int index = Integer.parseInt(refNode.getAttribute("index"));
                setting.index = index;
                settings.add(setting);
            }
        }
        settings.sort(Comparator.comparing(s -> s.index));
        for (Setting setting : settings) {
            if (setting.eReference.isMany()) {
                EList eList = (EList) setting.referenceeObject.eGet(setting.eReference);
                eList.add(setting.index >= 0 ? setting.index : eList.size(), setting.refObject);
            } else {
                setting.referenceeObject.eSet(setting.eReference, setting.refObject);
            }
        }
        eObject.eResource().save(null);
        return eObject;
    }

    private static class Setting {
        EObject referenceeObject;
        EReference eReference;
        EObject refObject;
        int index;
    }

    public void importDir(Path root) throws IOException {
        Files.walk(root).filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().toLowerCase().endsWith(XMI))
                .forEach(path -> {
                    try {
                        byte[] bytes = Files.readAllBytes(path);
                        hbServer.inTransaction(false, tx -> importResource(path.toString(), bytes, tx));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        Files.walk(root).filter(Files::isRegularFile)
                .filter(file -> file.getFileName().toString().toLowerCase().endsWith(REFS_XML))
                .forEach(path -> {
                    try {
                        byte[] bytes = Files.readAllBytes(path);
                        hbServer.inTransaction(false, tx -> importExternalReferences(bytes, tx));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    public void importZipFile(Path zipFile) {
        Map<String, Object> env = new HashMap<>();
        env.put("useTempFile", Boolean.TRUE);
        java.net.URI uri = java.net.URI.create("jar:" + zipFile.toUri());
        try (FileSystem fileSystem = FileSystems.newFileSystem(uri, env);) {
            for (Path root: fileSystem.getRootDirectories()) {
                importDir(root);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public int unzip(InputStream inputStream) throws Exception {
        int entityCount = 0;
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream);) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory()) {
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = zipInputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                    String entryName = zipEntry.getName();
                    if (entryName.toLowerCase().endsWith(XMI)) {
                        hbServer.inTransaction(false, tx -> importResource(entryName, outputStream.toByteArray(), tx));
                        ++entityCount;
                    }
                    else if (entryName.toLowerCase().endsWith(REFS_XML)) {
                        hbServer.inTransaction(false, tx -> importExternalReferences(outputStream.toByteArray(), tx));
                    }
                }
                zipEntry = zipInputStream.getNextEntry();
            }
        }
        return entityCount;
    }
}
