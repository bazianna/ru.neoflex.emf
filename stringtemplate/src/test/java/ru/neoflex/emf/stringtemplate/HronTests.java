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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@SpringBootTest(properties = {
        "db-name=hrontest"
}, classes = {ru.neoflex.emf.restserver.DBServerSvc.class})
public class HronTests {
    Logger logger = Logger.getLogger(StringTemplateController.class.getName());

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
        Map<String, Object> resourceElement = resourceToMap(resource);
        ClassPathResource stPattern = new ClassPathResource("hron.stg");
        STGroup hronStg = new STGroupFile(stPattern.getURL());
        ST hronSt = hronStg.getInstanceOf("resource");
        hronSt.add("resource", resourceElement);
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
        for (EObject eObject: eObjects) {
            Map<String, Object> eObjectElement = eObjectToMap(eObject);
            eObjectList.add(eObjectElement);
        }
        return eObjectList;
    }

    private Map<String, Object> refObjectToMap(EObject eObject) {
        EObject rootObject = EcoreUtil.getRootContainer(eObject);
        Map<String, Object> eObjectElement = new HashMap<>();
        EClass eClass = rootObject.eClass();
        String nsPrefix = eClass.getEPackage().getNsPrefix();
        String className = nsPrefix + "." + eClass.getName();
        eObjectElement.put("eClass", className);
        String id = EcoreUtil.getID(rootObject);
        eObjectElement.put("id", id);
        if (rootObject != eObject) {
            eObjectElement.put("path", EcoreUtil.getRelativeURIFragmentPath(rootObject, eObject));
        }
        return eObjectElement;
    }

    private Map<String, Object> eObjectToMap(EObject eObject) {
        Map<String, Object> eObjectElement = new HashMap<>();
        EClass eClass = eObject.eClass();
        String nsPrefix = eClass.getEPackage().getNsPrefix();
        String className = nsPrefix + "." + eClass.getName();
        eObjectElement.put("eClass", className);
        List<Map<String, Object>> eFeatures = new ArrayList<>();
        eObjectElement.put("eFeatures", eFeatures);
        for (EStructuralFeature sf: eClass.getEAllStructuralFeatures()) {
            if (!sf.isTransient() && !sf.isDerived() && eObject.eIsSet(sf)) {
                Map<String, Object> eFeature = new HashMap<>();
                eFeature.put("name", sf.getName());
                if (sf instanceof EAttribute) {
                    EAttribute eAttribute = (EAttribute) sf;
                    EDataType eDataType = eAttribute.getEAttributeType();
                    if (!sf.isMany()) {
                        String attribute = EcoreUtil.convertToString(eDataType, eObject.eGet(sf));
                        eFeature.put("attribute", attribute);
                    }
                    else {
                        List<String> attributes = new ArrayList<>();
                        eFeature.put("attributes", attributes);
                        for (Object attributeObj: (List) eObject.eGet(sf)) {
                            attributes.add(EcoreUtil.convertToString(eDataType, attributeObj));
                        }
                    }
                }
                else {
                    EReference eReference = (EReference) sf;
                    if (eReference.isContainer()) {
                        continue;
                    }
                    if (!sf.isMany()) {
                        EObject refObject = (EObject) eObject.eGet(sf);
                        if (eReference.isContainment()) {
                            eFeature.put("eObject", eObjectToMap(refObject));
                        }
                        else {

                        }
                    }
                    else {
                        List<EObject> refObjects = (List<EObject>) eObject.eGet(sf);
                        if (eReference.isContainment()) {
                            eFeature.put("eObjects", eObjectsToList(refObjects));
                        }
                        else {

                        }
                    }
                }
                eFeatures.add(eFeature);
            }
        }
        return eObjectElement;
    }
}