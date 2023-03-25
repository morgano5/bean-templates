package au.id.villar.utils.beangen;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Contains information about a bean to be generated and is responsible for writing the bean to be generated into
 * a @{@link java.io.Writer}.  */
class BeanDefinition {

	private static final String GENERATOR_NAME = "Simple bean generator";
	private static final String LINE_END = System.lineSeparator();
	private static final String INDENTATION_UNIT = "\t";

	private final boolean entityTemplate;
	private final String qualifiedName;
	private final String packageName;
	private final String singleName;
	private final String extendsFrom;
	private String entityName;
	private boolean withSetters;
	private ConstructorDefinition builderConstructor;
	private List<String> typeParameters = new ArrayList<>();
	private List<ConstructorDefinition> constructors = new ArrayList<>();
	private List<PropertyDefinition> properties = new ArrayList<>();

	/**
	 * Creates a new BeanDefinition
	 * @param entityTemplate true if this BeanDefinition represents a bean generated from an @{@link BeanTemplate}
	 * annotation, false if a @{@link Bean} annotation is used instead.
	 * @param extendsFrom class name of the superclass of the bean to be generated
	 */
	public BeanDefinition(boolean entityTemplate, String qualifiedName, String extendsFrom) {

		this.entityTemplate = entityTemplate;
		this.qualifiedName = qualifiedName;
		this.extendsFrom = extendsFrom;

		this.packageName = qualifiedName.indexOf('.') > -1
				? qualifiedName.substring(0, qualifiedName.lastIndexOf('.'))
				: "";

		this.singleName = qualifiedName.indexOf('.') > -1
				? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
				: qualifiedName;
	}

	public String getQualifiedName() {
		return qualifiedName;
	}

	public String getExtendsFrom() {
		return extendsFrom;
	}

	public String getPackageName() {
		return packageName;
	}

	public String getSingleName() {
		return singleName;
	}

	public String getEntityName() {
		return entityName;
	}

	public void setEntityName(String entityName) {
		this.entityName = entityName;
	}

	public boolean isWithSetters() {
		return withSetters;
	}

	public void setWithSetters(boolean withSetters) {
		this.withSetters = withSetters;
	}

	public List<String> getTypeParameters() {
		return typeParameters;
	}

	public void setTypeParameters(List<String> typeParameters) {
		this.typeParameters = typeParameters != null ? typeParameters : new ArrayList<>();
	}

	public List<ConstructorDefinition> getConstructors() {
		return constructors;
	}

	public void setConstructors(List<ConstructorDefinition> constructors) {
		this.constructors = constructors != null ? constructors : new ArrayList<>();
		this.builderConstructor = this.constructors.stream()
				.filter(ConstructorDefinition::usedByBuilder)
				.findFirst()
				.orElse(null);
	}

	public List<PropertyDefinition> getProperties() {
		return properties;
	}

	public void setProperties(List<PropertyDefinition> properties) {
		this.properties = properties != null ? properties : new ArrayList<>();
	}

	void writeSourceCode(Writer writer) throws IOException {
		writePackage(writer);
		writeImports(writer);
		writeClassNameDeclaration(writer);
		writeConstructors(writer);
		writeGettersAndSetters(writer);
		writeBuilder(writer);

		// TODO add support for "toString", "equals", "hashCode" (writeToString(), writeEquals(), writeHashCode())

		writeClassEnding(writer);
	}

	private void writeClassEnding(Writer writer) throws IOException {
		writer.append("}").append(LINE_END);
	}

	private void writeImports(Writer writer) throws IOException {
		List<String> imports = new ArrayList<>();

		imports.add("javax.annotation.processing.Generated");
		if (entityTemplate) {
			imports.add("javax.persistence.Entity");
		}

		// TODO Is that worth simplifying things like "private java.math.BigDecimal myNumber" to "private BigDecimal nyNumber" and then adding an import statement?

		Collections.sort(imports);

		for (String importStatement : imports) {
			writer.append("import ").append(importStatement).append(";").append(LINE_END);
		}
		writer.append(LINE_END);
	}

	private void writePackage(Writer writer) throws IOException {
		final String packageName = getPackageName();

		if (!packageName.isEmpty()) {
			writer.append("package ").append(packageName).append(";").append(LINE_END).append(LINE_END);
		}
	}

	private void writeClassNameDeclaration(Writer writer) throws IOException {
		writer.append("@Generated(\"").append(GENERATOR_NAME).append("\")").append(LINE_END);
		if (entityTemplate) {
			writer.append("@Entity");
			if (entityName != null && !entityName.isEmpty()) {
				writer.append("(name=\"").append(entityName).append("\")");
			}
			writer.append(LINE_END);
		}
		final String superClassName = inferSuperClassName();
		writer.append("public class ").append(getSingleName());
		writeTypeParameters(writer);
		writer.append(" extends ").append(superClassName).append(" {").append(LINE_END);
	}

	private void writeConstructors(Writer writer) throws IOException {

		if (constructors.size() == 0) {
			writeConstructor(writer, new ConstructorDefinition("public", Collections.emptyList(), false));
			return;
		}

		for (ConstructorDefinition constructor : constructors) {
			writeConstructor(writer, constructor);
		}
	}

	private void writeGettersAndSetters(Writer writer) throws IOException {

		for (PropertyDefinition property : properties) {
			if (property.needsGetter()) {
				writer.append(LINE_END).append(indentation(1)).append("public ").append(property.getType())
						.append(" get").append(Character.toUpperCase(property.getName().charAt(0)))
						.append(property.getName().substring(1)).append("() {").append(LINE_END)
						.append(indentation(2)).append("return ").append(property.getName()).append(";")
						.append(LINE_END)
						.append(indentation(1)).append("}").append(LINE_END);
			}

			if (!property.isFinal() && property.needsSetter() && withSetters) {
				writer.append(LINE_END)
						.append(indentation(1)).append("public void set")
						.append(Character.toUpperCase(property.getName().charAt(0)))
						.append(property.getName().substring(1)).append("(").append(property.getType())
						.append(" ").append(property.getName()).append(") {").append(LINE_END)
						.append(indentation(2)).append("this.").append(property.getName())
						.append(" = ").append(property.getName()).append(";").append(LINE_END)
						.append(indentation(1)).append("}").append(LINE_END);
			}
		}
	}

	private void writeTypeParameters(Writer writer) throws IOException {
		if (!typeParameters.isEmpty()) {
			writer.append("<").append(String.join(", ", typeParameters)).append(">");
		}
	}

	private void writeConstructor(Writer writer, ConstructorDefinition constructor)
			throws IOException {
		writer.append(LINE_END)
				.append(indentation(1)).append(constructor.accessModifier()).append(" ").append(getSingleName())
				.append("(").append(constructor.parameters().stream()
						.map(p -> p.type() + " " + p.name())
						.collect(Collectors.joining(", ")))
				.append(") {").append(LINE_END).append(indentation(2))
				.append("super(")
				.append(constructor.parameters().stream()
						.map(VariableDefinition::name)
						.collect(Collectors.joining(", ")))
				.append(");")
				.append(LINE_END).append(indentation(1)).append("}").append(LINE_END);
	}

	private String inferSuperClassName() {

		final String superClassPackage = extendsFrom.indexOf('.') > -1
				? extendsFrom.substring(0, extendsFrom.lastIndexOf('.'))
				: "";

		return superClassPackage.equals(getPackageName())
				? extendsFrom.substring(superClassPackage.length() + 1)
				: extendsFrom;
	}

	private void writeBuilder(Writer writer) throws IOException {

		if (builderConstructor == null) {
			return;
		}

		writeBuilderNameDeclaration(writer);
		writeBuilderPrivateProperties(writer);
		writeBuilderFluidMethods(writer);
		writeBuilderBuildMethod(writer);
		writeBuilderClassEnding(writer);
		writeBuilderCreatorMethod(writer);
		writeBuilderToBuilderMethod(writer);
	}

	private void writeBuilderNameDeclaration(Writer writer) throws IOException {
		writer.append(LINE_END).append(LINE_END)
				.append(indentation(1)).append("public static class ").append(getSingleName()).append("Builder");
		writeTypeParameters(writer);
		writer.append(" {").append(LINE_END);
	}

	private void writeBuilderPrivateProperties(Writer writer) throws IOException {
		for (VariableDefinition property : builderConstructor.parameters()) {
			writer.append(LINE_END)
					.append(indentation(2)).append("private ").append(property.type()).append(" ")
					.append(property.name()).append(";").append(LINE_END);
		}
	}

	private void writeBuilderFluidMethods(Writer writer) throws IOException {
		for (VariableDefinition property : builderConstructor.parameters()) {
			writer.append(LINE_END)
					.append(indentation(2)).append("public ").append(getSingleName()).append("Builder")
					.append(" ").append(property.name()).append("(").append(property.type()).append(" ")
					.append(property.name()).append(") {").append(LINE_END)
					.append(indentation(3)).append("this.").append(property.name()).append(" = ")
					.append(property.name()).append(";").append(LINE_END)
					.append(indentation(3)).append("return this;").append(LINE_END)
					.append(indentation(2)).append("}").append(LINE_END);
		}
	}

	private void writeBuilderBuildMethod(Writer writer) throws IOException {
		writer.append(LINE_END).append(indentation(2)).append("public ").append(getSingleName()).append(" build() {")
				.append(LINE_END)
				.append(indentation(3)).append("return new ").append(getSingleName())
				.append("(")
				.append(builderConstructor.parameters().stream().map(p -> p.name()).collect(Collectors.joining(", ")))
				.append(");").append(LINE_END)
				.append(indentation(2)).append("}").append(LINE_END);
	}

	private void writeBuilderClassEnding(Writer writer) throws IOException {
		writer.append(indentation(1)).append("}").append(LINE_END);
	}

	private void writeBuilderCreatorMethod(Writer writer) throws IOException {
		writer.append(LINE_END)
				.append(indentation(1)).append("public static ").append(getSingleName())
				.append("Builder builder() {").append(LINE_END)
				.append(indentation(2)).append("return new ").append(getSingleName()).append("Builder();")
				.append(LINE_END)
				.append(indentation(1)).append("}").append(LINE_END);
	}

	private void writeBuilderToBuilderMethod(Writer writer) throws IOException {
		writer.append(LINE_END)
				.append(indentation(1)).append("public ").append(getSingleName()).append("Builder toBuilder() {")
				.append(LINE_END)
				.append(indentation(2)).append("return new ").append(getSingleName()).append("Builder()")
				.append(LINE_END)
				.append(builderConstructor.parameters().stream()
						.map(p -> indentation(3) + "." + p.name() + "(" + p.name() + ")")
						.collect(Collectors.joining(LINE_END)))
				.append(";").append(LINE_END)
				.append(indentation(1)).append("}").append(LINE_END);
	}

	private String indentation(int n) {
		return INDENTATION_UNIT.repeat(n);
	}
}
