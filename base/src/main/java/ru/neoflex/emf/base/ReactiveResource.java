package ru.neoflex.emf.base;

import org.eclipse.emf.ecore.resource.Resource;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface ReactiveResource {
    CompletionStage<Resource> reactiveSave(Map<?, ?> options);
    CompletionStage<Resource>  reactiveLoad(Map<?, ?> options);
    CompletionStage<Resource> reactiveDelete(Map<?, ?> options);
}
