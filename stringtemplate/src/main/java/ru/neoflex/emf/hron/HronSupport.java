package ru.neoflex.emf.hron;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;

public interface HronSupport {
    EObject lookupEObject(EClass eClass, String qName);

    EClass lookupEClass(String nsPrefix, String name);
}
