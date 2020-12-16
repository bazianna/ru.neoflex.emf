package ru.neoflex.emf.hron;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.ResourceSet;

public interface HronSupport {
    EObject lookupEObject(ResourceSet rs, EClass eClass, String qName);

    EClass lookupEClass(ResourceSet rs, String nsPrefix, String name);
}
