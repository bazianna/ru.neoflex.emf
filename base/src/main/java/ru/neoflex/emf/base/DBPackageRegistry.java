package ru.neoflex.emf.base;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.impl.EPackageRegistryImpl;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.util.List;
import java.util.stream.Collectors;

public class DBPackageRegistry extends EPackageRegistryImpl {
    private final DBTransaction tx;

    public DBPackageRegistry(EPackage.Registry delegateRegistry, DBTransaction tx) {
        super(delegateRegistry);
        this.tx = tx;
    }

    public EPackage getEPackage(String nsURI) {
        EPackage result = super.getEPackage(nsURI);
        if (result == null) {
            ResourceSet resourceSet = tx.createResourceSet();
            List<EPackage> ePackages = tx.findByClassAndQName(resourceSet, EcorePackage.Literals.EPACKAGE, nsURI)
                    .flatMap(resource -> resource.getContents().stream())
                    .map(eObject -> (EPackage) eObject)
                    .collect(Collectors.toList());
            ePackages.forEach(ePackage -> tx.getDbServer().registerEPackage(ePackage));
            if (ePackages.size() > 0) {
                result = ePackages.get(0);
            }
        }
        return result;
    }
}
