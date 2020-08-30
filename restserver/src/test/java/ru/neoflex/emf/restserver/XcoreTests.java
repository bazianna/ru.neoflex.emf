package ru.neoflex.emf.restserver;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.plugin.EcorePlugin;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xcore.XcoreStandaloneSetup;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

@SpringBootTest
public class XcoreTests {
    @Autowired
    private DBServerSvc dbServerSvc;

    {
        org.eclipse.xtext.ecore.EcoreSupport x;
        org.eclipse.emf.codegen.ecore.xtext.GenModelSupport y;
        org.eclipse.emf.ecore.xcore.XcoreStandaloneSetup z;
        XcoreStandaloneSetup.doSetup();
    }

    @Test
    public void loadXcore() throws Exception {
        XtextResourceSet rs = new XtextResourceSet();
        //rs.getURIConverter().getURIMap().putAll(EcorePlugin.computePlatformURIMap(true));
        rs.getURIResourceMap().put(
                URI.createURI("platform:/resource/org.eclipse.emf.ecore/model/Ecore.ecore"),
                EcorePackage.eINSTANCE.eResource()
        );
        String baseEcoreUrl = EcorePlugin.INSTANCE.getBaseURL().toExternalForm();
        rs.getURIConverter().getURIMap().put(
                URI.createURI("platform:/resource/org.eclipse.emf.ecore/"),
                URI.createURI(baseEcoreUrl)
        );
        ClassPathResource xresource = new ClassPathResource("metamodel/model.xcore");
        Resource resource = rs.createResource(URI.createURI("file:///model.xcore"));
        try (InputStream is = xresource.getInputStream()) {
            resource.load(is, null);
        }
        List<EObject> eObjects = resource.getContents();
        Assert.notNull(resource);
        ResourceSet ers = new ResourceSetImpl();
        ers.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore", new EcoreResourceFactoryImpl());
        Resource er = ers.createResource(URI.createURI("file:///model.ecore"));
        er.getContents().add(EcoreUtil.copy(eObjects.get(2)));
        Assert.notNull(er);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        er.save(os, null);
        String ecore = os.toString();
        Assert.notNull(ecore);
    }

    @Test
    public void loadXcore2() throws Exception {
        dbServerSvc.getDbServer().inTransaction(false, tx -> {
            XtextResourceSet xrs = new XtextResourceSet();
            xrs.getURIResourceMap().put(
                    URI.createURI("platform:/resource/org.eclipse.emf.ecore/model/Ecore.ecore"),
                    EcorePackage.eINSTANCE.eResource()
            );
            String baseEcoreUrl = EcorePlugin.INSTANCE.getBaseURL().toExternalForm();
            xrs.getURIConverter().getURIMap().put(
                    URI.createURI("platform:/resource/org.eclipse.emf.ecore/"),
                    URI.createURI(baseEcoreUrl)
            );
            ClassPathResource model = new ClassPathResource("metamodel/model.xcore");
            Resource xresource = xrs.createResource(URI.createURI("file:///model.xcore"));
            try (InputStream is = model.getInputStream()) {
                xresource.load(is, null);
            }
            List<EObject> eObjects = xresource.getContents();
            EPackage ePackage = (EPackage) eObjects.get(2);
            ResourceSet rs = tx.createResourceSet();
            Resource resource = rs.createResource(tx.getDbServer().createURI(""));
            resource.getContents().add(ePackage);
            resource.save(null);
            return resource;
        });
    }
}
