package ru.neoflex.emf.hron;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import ru.neoflex.emf.base.HbServer;

import java.util.HashMap;
import java.util.Map;

public class HbHronSupport implements HronSupport {
    HbServer hbServer;
    Map<String, EClass> nameToEClassMap = new HashMap<>();

    public HbHronSupport(HbServer hbServer) {
        this.hbServer = hbServer;
        for(Object o: hbServer.getPackageRegistry().values()) {
            EPackage ePackage = (EPackage) o;
            String nsPrefix = ePackage.getNsPrefix();
            for (EClassifier c: ePackage.getEClassifiers()) {
                if (c instanceof EClass) {
                    EClass eClass = (EClass) c;
                    if (!eClass.isAbstract()) {
                        String name = eClass.getName();
                        nameToEClassMap.put(nsPrefix + "." + name, eClass);
                    }
                }
            }
        }
    }

    @Override
    public EObject lookupEObject(EClass eClass, String qName) {
        try {
            return hbServer.inTransaction(true, tx->{
                return tx.findByClassAndQName(tx.getResourceSet(), eClass, qName).findFirst().get().getContents().get(0);
            });
        } catch (Exception e) {
            throw new IllegalArgumentException("EObject not found " +
                    eClass.getEPackage().getNsPrefix() + "." + eClass.getName() + "/" +qName, e);
        }
    }

    @Override
    public EClass lookupEClass(String nsPrefix, String name) {
        EClass eClass = nameToEClassMap.get(nsPrefix + "." + name);
        if (eClass == null) {
            throw new IllegalArgumentException("EClass not found " +
                    nsPrefix + "." + name);
        }
        return eClass;
    }
}
