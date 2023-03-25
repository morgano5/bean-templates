package au.id.villar.utils.beangen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

public class BeanTemplateProcessor extends AbstractProcessor {

    public static final String BEAN_DEFINITION_SUFFIX_CONVENTION = "Template";

    private static final Set<String> SUPPORTED_ANNOTATIONS = Set.of(
            BeanTemplate.class.getCanonicalName(),
            Bean.class.getCanonicalName()
    );

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SUPPORTED_ANNOTATIONS;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        List<BeanDefinition> beanDefinitions = annotations.stream()
                .filter(t -> SUPPORTED_ANNOTATIONS.contains(t.getQualifiedName().toString()))
                .flatMap(t -> roundEnv.getElementsAnnotatedWith(t).stream()
                        .filter(e -> e instanceof TypeElement)
                        .map(TypeElement.class::cast)
                        .map(e -> createBeanDefinition(e, t)))
                .toList();

        // TODO validateDefinition(definition);
        beanDefinitions.forEach(this::generateSourceCode);

        return annotations.stream().allMatch(a -> SUPPORTED_ANNOTATIONS.contains(a.getQualifiedName().toString()));
    }

    private BeanDefinition createBeanDefinition(TypeElement element, TypeElement annotation) {
        final String extendsFrom = element.getQualifiedName().toString();
        final String annotationName = annotation.getQualifiedName().toString();
        final boolean isEntityTemplate = annotationName.equals(BeanTemplate.class.getCanonicalName());
        final String beanName = inferGeneratedBeanName(element);
        final BeanDefinition definition = new BeanDefinition(isEntityTemplate, beanName, extendsFrom);

        if (isEntityTemplate) {
            definition.setEntityName(inferEntityName(element));
            definition.setWithSetters(true);
        } else {
            definition.setWithSetters(inferWithSetters(element));
        }

        definition.setTypeParameters(inferTypeParameters(element));
        definition.setConstructors(inferConstructors(element));
        definition.setProperties(inferPropertyList(element));

        return definition;
    }

    private void generateSourceCode(BeanDefinition definition) {

        final String beanName = definition.getQualifiedName();

        try {
            final JavaFileObject file = processingEnv.getFiler().createSourceFile(beanName);
            try (Writer fileWriter = file.openWriter()) {
                definition.writeSourceCode(fileWriter);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String inferEntityName(TypeElement element) {
        return getValueFromAnnotationAttribute("name", String.class, element);
    }

    private String inferGeneratedBeanName(TypeElement element) {

        final String typeName = getValueFromAnnotationAttribute("typeName", String.class, element);

        if (typeName != null) {
            return typeName.indexOf('.') != -1 ? typeName : inferPackageName(element) + '.' + typeName;
        }

        final String beanDefinitionName = element.getQualifiedName().toString();

        if (beanDefinitionName.endsWith(BEAN_DEFINITION_SUFFIX_CONVENTION)) {
            return beanDefinitionName.substring(0,
                    beanDefinitionName.length() - BEAN_DEFINITION_SUFFIX_CONVENTION.length());
        }

        final String errorMessage = "Couldn't infer a proper generated name for " + beanDefinitionName;

        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, errorMessage);

        throw new IllegalArgumentException(errorMessage);
    }

    private List<String> inferTypeParameters(TypeElement element) {
        return element.getTypeParameters().stream().map(t -> t.getSimpleName().toString()).toList();
    }

    private boolean inferWithSetters(TypeElement element) {
        return Boolean.TRUE.equals(getValueFromAnnotationAttribute("setters", Boolean.class, element));
    }

    private List<ConstructorDefinition> inferConstructors(TypeElement element) {
        final List<ConstructorDefinition> constructors = element.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.CONSTRUCTOR && e instanceof ExecutableElement)
                .map(ExecutableElement.class::cast)
                .filter(e -> !e.getModifiers().stream()
                        .map(Modifier::toString).collect(Collectors.toSet()).contains("private"))
                .map(this::toConstructorDefinition)
                .toList();
        final boolean needsNoArgConstructor = inferNeedsNoArgsConstructor(element);
        final boolean hasNoArgsConstructor = constructors.stream().anyMatch(c -> c.parameters().isEmpty());
        return needsNoArgConstructor && !hasNoArgsConstructor
                ? Stream.concat(constructors.stream(), Stream.of(new ConstructorDefinition("public", List.of(), false))).toList()
                : constructors;
    }

    private boolean inferNeedsNoArgsConstructor(TypeElement element) {
        return Boolean.TRUE.equals(getValueFromAnnotationAttribute("noArgsConstructor", Boolean.class, element));
    }

    private ConstructorDefinition toConstructorDefinition(ExecutableElement element) {

        final Set<String> modifiers = element.getModifiers().stream()
                .map(Modifier::toString)
                .collect(Collectors.toSet());

        final String modifier = modifiers.contains("protected")
                ? "protected"
                : (modifiers.contains("public") ? "public" : null);

        final List<VariableDefinition> parameters = element.getParameters().stream()
                .map(v -> new VariableDefinition(v.getSimpleName().toString(), v.asType().toString()))
                .toList();

        boolean usedByAnnotation = Optional.ofNullable(element.getAnnotation(Builder.class)).isPresent();

        return new ConstructorDefinition(modifier, parameters, usedByAnnotation);
    }

    private List<PropertyDefinition> inferPropertyList(TypeElement element) {

        Map<String, PropertyDefinition> properties = element.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.FIELD && e instanceof VariableElement)
                .map(VariableElement.class::cast)
                .filter(e -> e.getModifiers().stream().noneMatch(m -> m.toString().equals("static")))
                .map(this::toPropertyDefinition)
                .collect(Collectors.toMap(PropertyDefinition::getName, p -> p));

        element.getEnclosedElements().stream()
                .filter(e -> e.getKind() == ElementKind.METHOD && e instanceof ExecutableElement)
                .map(ExecutableElement.class::cast)
                .forEach(e -> checkWithFields(properties, e));

        return new ArrayList<>(properties.values());
    }

    private PropertyDefinition toPropertyDefinition(VariableElement field) {
        PropertyDefinition property
                = new PropertyDefinition(field.getSimpleName().toString(), field.asType().toString());
        property.setFinal(field.getModifiers().stream().anyMatch(m -> m.toString().equals("final")));
        return property;
    }

    private void checkWithFields(Map<String, PropertyDefinition> properties, ExecutableElement element) {
        checkForGetter(properties, element);
        checkForSetter(properties, element);
    }

    private void checkForGetter(Map<String, PropertyDefinition> properties, ExecutableElement element) {
        Optional.of(element)
                .filter(e -> e.getParameters().size() == 0)
                .map(e -> element.getSimpleName())
                .filter(n -> n.length() > 3 && n.toString().startsWith("get"))
                .map(n -> Character.toLowerCase(n.charAt(3)) + n.toString().substring(4))
                .map(properties::get)
                .ifPresent(p -> p.setNeedsGetter(false));
    }

    private void checkForSetter(Map<String, PropertyDefinition> properties, ExecutableElement element) {
        Optional.of(element)
                .filter(e -> e.getParameters().size() == 1)
                .map(e -> element.getSimpleName())
                .filter(n -> n.length() > 3 && n.toString().startsWith("set"))
                .map(n -> Character.toLowerCase(n.charAt(3)) + n.toString().substring(4))
                .map(properties::get)
                .filter(p -> p.getType().equals(element.getParameters().get(0).asType().toString()))
                .ifPresent(p -> p.setNeedsSetter(false));
    }

    private String inferPackageName(TypeElement element) {

        if(!(element.getEnclosingElement() instanceof PackageElement packageElement)) {
            throw new IllegalArgumentException("Inner classes not supported");
        }

        return packageElement.getQualifiedName().toString();
    }

    private <R> R getValueFromAnnotationAttribute(String attributeName, Class<R> type, TypeElement element) {
        return element.getAnnotationMirrors().stream()
                .filter(a -> a.getAnnotationType().toString().equals(BeanTemplate.class.getCanonicalName()))
                .map(a -> a.getElementValues().entrySet())
                .flatMap(Collection::stream)
                .filter(e -> e.getKey().getSimpleName().toString().equals(attributeName))
                .map(e -> type.cast(e.getValue().getValue()))
                .findFirst()
                .orElse(null);
    }

}
