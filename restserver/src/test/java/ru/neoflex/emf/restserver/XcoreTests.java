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
import ru.neoflex.emf.base.DBTransaction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SpringBootTest
public class XcoreTests {
    @Autowired
    private DBServerSvc dbServerSvc;

    {
        XcoreStandaloneSetup.doSetup();
    }

    @Test
    public void loadXcore() throws Exception {
        ClassPathResource xresource = new ClassPathResource("metamodel/model.xcore");
        EPackage ePackage;
        try (InputStream is = xresource.getInputStream()) {
            ePackage = dbServerSvc.loadXcorePackage(is, null);
        }
        Stream<EPackage> ePackageStream = Stream.of(ePackage);
        byte[] ecore = DBServerSvc.ePackages2Ecore(ePackageStream);
        Assert.isTrue(ecore.length > 0, "Ecore generated");
    }

    @Test
    public void loadXcore2() throws Exception {
        dbServerSvc.getDbServer().inTransaction(false, DBTransaction::truncate);
        ClassPathResource xresource = new ClassPathResource("metamodel/model.xcore");
        try (InputStream is = xresource.getInputStream()) {
            Resource resource = dbServerSvc.uploadXcore(is, null);
        }
    }
}
