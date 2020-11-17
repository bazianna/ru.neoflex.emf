package ru.neoflex.emf.base;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Events {
    private List<Consumer<EObject>> afterLoadList = new ArrayList<>();
    public void fireAfterLoad(EObject eObject) {
        for (Consumer<EObject> consumer: afterLoadList) {
            consumer.accept(eObject);
        }
    }
    public void registerAfterLoad(Consumer<EObject> consumer) {
        afterLoadList.add(consumer);
    }

    private List<BiConsumer<EObject, EObject>> beforeSaveList = new ArrayList<>();
    public void fireBeforeSave(EObject old, EObject eObject) {
        for (BiConsumer<EObject, EObject> consumer: beforeSaveList) {
            consumer.accept(old, eObject);
        }
    }
    public void registerBeforeSave(BiConsumer<EObject, EObject> consumer) {
        beforeSaveList.add(consumer);
    }

    private List<BiConsumer<EObject, EObject>> afterSaveList = new ArrayList<>();
    public void fireAfterSave(EObject old, EObject eObject) {
        for (BiConsumer<EObject, EObject> handler: afterSaveList) {
            handler.accept(old, eObject);
        }
    }
    public void registerAfterSave(BiConsumer<EObject, EObject> consumer) {
        afterSaveList.add(consumer);
    }

    private List<Consumer<EObject>> beforeDeleteList = new ArrayList<>();
    public void fireBeforeDelete(EObject eObject) {
        for (Consumer<EObject> consumer: beforeDeleteList) {
            consumer.accept(eObject);
        }
    }
    public void registerBeforeDelete(Consumer<EObject> consumer) {
        beforeDeleteList.add(consumer);
    }
}
