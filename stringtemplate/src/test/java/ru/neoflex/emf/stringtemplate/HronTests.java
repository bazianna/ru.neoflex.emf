package ru.neoflex.emf.stringtemplate;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CodePointBuffer;
import org.antlr.v4.runtime.CodePointCharStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;
import ru.neoflex.emf.hron.HbHronSupport;
import ru.neoflex.emf.hron.HronEvaluator;
import ru.neoflex.emf.hron.HronLexer;
import ru.neoflex.emf.hron.HronParser;
import ru.neoflex.emf.restserver.DBServerSvc;

import javax.annotation.PostConstruct;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

@SpringBootTest(properties = {
        "db-name=hrontest"
}, classes = {ru.neoflex.emf.restserver.DBServerSvc.class})
public class HronTests {
    Logger logger = Logger.getLogger(HronTests.class.getName());

    @Autowired
    private DBServerSvc dbServerSvc;

    @PostConstruct
    void init() {
        dbServerSvc.getDbServer().registerEPackage(StringtemplatePackage.eINSTANCE);
    }

    @Test
    public void loadMyClass() throws Exception {
        ClassPathResource myClassResource = new ClassPathResource("myClass.hron");
        String code = new String(Files.readAllBytes(myClassResource.getFile().toPath()), StandardCharsets.UTF_8);
        CharStream input = CodePointCharStream.fromBuffer(
                CodePointBuffer.withChars(CharBuffer.wrap(code.toCharArray())));
        HronLexer lexer = new HronLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        HronParser parser = new HronParser(tokens);
        ParseTree tree = parser.resource();
        Resource resource = dbServerSvc.getDbServer().createResource();
        HronEvaluator evaluator = new HronEvaluator(resource, new HbHronSupport(dbServerSvc.getDbServer()));
        ParseTreeWalker walker = new ParseTreeWalker();
        walker.walk(evaluator, tree);
        evaluator.setPhase(HronEvaluator.Phase.NONCONTAINMENT);
        walker.walk(evaluator, tree);
//        resource.save(null);
        Map<String, Object> resourceMap = resourceToMap(resource);
        ClassPathResource stPattern = new ClassPathResource("hron.stg");
        STGroup hronStg = new STGroupFile(stPattern.getURL());
        ST hronSt = hronStg.getInstanceOf("resource");
        hronSt.add("resource", resourceMap);
        String s = hronSt.render();
        logger.info(s);
    }

    Map<String, Object> resourceToMap(Resource resource) {
        Map<String, Object> resourceElement = new HashMap<>();
        List<EObject> eObjects = resource.getContents();
        List<Map<String, Object>> eObjectList = eObjectsToList(eObjects);
        resourceElement.put("eObjects", eObjectList);
        return resourceElement;
    }

    private List<Map<String, Object>> eObjectsToList(List<EObject> eObjects) {
        List<Map<String, Object>> eObjectList = new ArrayList<>();
        for (EObject eObject : eObjects) {
            Map<String, Object> eObjectElement = eObjectToMap(eObject);
            eObjectList.add(eObjectElement);
        }
        return eObjectList;
    }

    private Map<EObject, Map<String, Object>> eObjectToMapMap = new IdentityHashMap<>();

    private Map<String, Object> refObjectToMap(EObject eObject, EObject refObject) {
        Map<String, Object> result = new HashMap<>();
        if (EcoreUtil.isAncestor(eObject.eResource(), refObject)) {
            Map<String, Object> refObjectMap = eObjectToMapMap.computeIfAbsent(refObject, k -> new HashMap<>());
            String label = eObject.eResource().getURIFragment(refObject).replaceAll("[^_0-9a-zA-Z]", "_");
            refObjectMap.put("label", label);
            result.put("label", label);
        } else {
            EObject rootObject = EcoreUtil.getRootContainer(refObject);
            EClass eClass = rootObject.eClass();
            String nsPrefix = eClass.getEPackage().getNsPrefix();
            String className = nsPrefix + "." + eClass.getName();
            result.put("eClass", className);
            String id = EcoreUtil.getID(rootObject);
            result.put("id", id);
            if (rootObject != eObject) {
                result.put("path", EcoreUtil.getRelativeURIFragmentPath(rootObject, refObject));
            }
        }
        return result;
    }

    private List<Map<String, Object>> refObjectsToList(EObject eObject, List<EObject> refObjects) {
        List<Map<String, Object>> eObjectList = new ArrayList<>();
        for (EObject refObject : refObjects) {
            Map<String, Object> refObjectMap = refObjectToMap(eObject, refObject);
            eObjectList.add(refObjectMap);
        }
        return eObjectList;
    }

    private Map<String, Object> eObjectToMap(EObject eObject) {
        Map<String, Object> eObjectMap = eObjectToMapMap.computeIfAbsent(eObject, k -> new HashMap<>());
        EClass eClass = eObject.eClass();
        String nsPrefix = eClass.getEPackage().getNsPrefix();
        String className = nsPrefix + "." + eClass.getName();
        eObjectMap.put("eClass", className);
        List<Map<String, Object>> eFeatures = new ArrayList<>();
        eObjectMap.put("eFeatures", eFeatures);
        for (EStructuralFeature sf : eClass.getEAllStructuralFeatures()) {
            if (!sf.isTransient() && !sf.isDerived() && eObject.eIsSet(sf)) {
                Map<String, Object> eFeature = new HashMap<>();
                eFeature.put("name", sf.getName());
                if (sf instanceof EAttribute) {
                    EAttribute eAttribute = (EAttribute) sf;
                    EDataType eDataType = eAttribute.getEAttributeType();
                    if (!sf.isMany()) {
                        Object value = eObject.eGet(sf);
                        String attribute = getAttributeString(eDataType, value);
                        eFeature.put("attribute", attribute);
                    } else {
                        List<String> attributes = new ArrayList<>();
                        eFeature.put("attributes", attributes);
                        for (Object attributeObj : (List) eObject.eGet(sf)) {
                            String attribute = getAttributeString(eDataType, attributeObj);
                            attributes.add(attribute);
                        }
                    }
                } else {
                    EReference eReference = (EReference) sf;
                    if (eReference.isContainer()) {
                        continue;
                    }
                    if (!sf.isMany()) {
                        EObject refObject = (EObject) eObject.eGet(sf);
                        if (eReference.isContainment()) {
                            eFeature.put("eObject", Collections.singletonList(eObjectToMap(refObject)));
                        } else {
                            eFeature.put("refObject", Collections.singletonList(refObjectToMap(eObject, refObject)));
                        }
                    } else {
                        List<EObject> refObjects = (List<EObject>) eObject.eGet(sf);
                        if (eReference.isContainment()) {
                            eFeature.put("eObjects", eObjectsToList(refObjects));
                        } else {
                            eFeature.put("refObjects", refObjectsToList(eObject, refObjects));
                        }
                    }
                }
                eFeatures.add(eFeature);
            }
        }
        return eObjectMap;
    }

    private String getAttributeString(EDataType eDataType, Object value) {
        return EcoreUtil.convertToString(eDataType, value)
                .replaceAll("\"", "\\\"")
                .replaceAll("\\\\", "\\\\");
    }
}