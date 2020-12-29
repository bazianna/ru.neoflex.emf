package ru.neoflex.emf.restserver;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import ru.neoflex.emf.base.HbServer;
import ru.neoflex.emf.hron.HronResourceSet;
import ru.neoflex.emf.hron.HronSupport;

import java.util.Map;

public class HbHronSupport implements HronSupport {
    protected HbServer hbServer;
    protected Map<String, EClass> nameToEClassMap;
    protected ResourceSet rs;

    public HbHronSupport(HbServer hbServer, ResourceSet rs) {
        this.hbServer = hbServer;
        this.rs = rs != null ? rs :hbServer.createResourceSet();
    }

    public HbHronSupport(HbServer hbServer) {
        this(hbServer, null);
    }

    @Override
    public EObject lookupEObject(EClass eClass, String qName) {
        if (EcorePackage.eINSTANCE.getEPackage().isSuperTypeOf(eClass)) {
            return hbServer.getPackageRegistry().getEPackage(qName);
        }
        Resource resource = hbServer.findBy(rs, eClass, qName);
        if (resource == null || resource.getContents().size() != 1) {
            return null;
        }
        return resource.getContents().get(0);
    }

    @Override
    public EClass lookupEClass(String nsPrefix, String name) {
        if (nameToEClassMap == null) {
            EPackage.Registry registry = hbServer.getPackageRegistry();
            nameToEClassMap = HronResourceSet.createNameToEClassMap(registry);
        }
        EClass eClass = nameToEClassMap.get(nsPrefix + "." + name);
        return eClass;
    }

}
