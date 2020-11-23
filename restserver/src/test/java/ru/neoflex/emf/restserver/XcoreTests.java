package ru.neoflex.emf.restserver;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xcore.XcoreStandaloneSetup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;
import ru.neoflex.emf.base.HbTransaction;

import java.io.InputStream;
import java.util.Collections;
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
        byte[] ecore = DBServerSvc.ePackages2Ecore(Collections.singletonList(ePackage));
        Assert.isTrue(ecore.length > 0, "Ecore generated");
    }

    @Test
    public void loadXcore2() throws Exception {
        dbServerSvc.getDbServer().inTransaction(false, HbTransaction::truncate);
        ClassPathResource xresource = new ClassPathResource("metamodel/model.xcore");
        try (InputStream is = xresource.getInputStream()) {
            Resource resource = dbServerSvc.uploadXcore(is, null);
        }
    }
}
